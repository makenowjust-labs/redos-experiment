// Usage: node run.ts <dataset> <output> (--image <ref> | --command <command>) [options]
//
// Runs a tool on every pattern of the dataset and appends one JSON line per pattern to the output.
// The first line of the output has the conditions of the run.
// Running it again with the same conditions continues from the patterns that the output already has.
//
// Options:
//   --image <ref>        the image of the tool
//   --command <command>  a command to run instead of an image, for development
//   --timeout <seconds>  the wall-clock limit of a pattern (default: 10)
//   --parallel <n>       the number of the tool processes (default: 1)
//   --memory <size>      the memory limit of a tool process (default: 4g)
//   --env <KEY=VALUE>    an environment variable of the tool, repeatable
//   --batch <n>          the number of the patterns of a request, for a tool that takes batches
//   --limit <n>          the number of the patterns to run from the head of the dataset

import { type ChildProcess, execFileSync, spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { appendFileSync, existsSync, readFileSync, writeFileSync } from "node:fs";
import os from "node:os";
import { parseArgs } from "node:util";

type Item = { source: string; flags?: string };

const StartTimeout = 120_000;

const { positionals, values } = parseArgs({
  allowPositionals: true,
  options: {
    image: { type: "string" },
    command: { type: "string" },
    timeout: { type: "string", default: "10" },
    parallel: { type: "string", default: "1" },
    memory: { type: "string", default: "4g" },
    env: { type: "string", multiple: true, default: [] },
    batch: { type: "string" },
    limit: { type: "string" },
  },
});
const [datasetPath, outputPath] = positionals;
if (datasetPath === undefined || outputPath === undefined || (values.image === undefined) === (values.command === undefined)) {
  console.error("Usage: node run.ts <dataset> <output> (--image <ref> | --command <command>) [options]");
  process.exit(1);
}
const image = values.image!;
const timeout = Number(values.timeout) * 1000;
const parallel = Number(values.parallel);
const batch = values.batch === undefined ? undefined : Number(values.batch);

const datasetText = readFileSync(datasetPath, "utf8");
const dataset: Item[] = datasetPath.endsWith(".jsonl")
  ? datasetText.split("\n").filter((line) => line !== "").map((line) => JSON.parse(line))
  : JSON.parse(datasetText);
const size = values.limit === undefined ? dataset.length : Math.min(Number(values.limit), dataset.length);

const docker = (...args: string[]): string => execFileSync("docker", args, { encoding: "utf8" }).trim();

const labels = (): Record<string, string> => {
  const all: Record<string, string> = JSON.parse(docker("image", "inspect", "--format", "{{json .Config.Labels}}", image)) ?? {};
  return Object.fromEntries(Object.entries(all).filter(([name]) => name.startsWith("codes.quine.labs.redos-experiment.")));
};

// The conditions that decide the results: a run continues an output only when they are the same.
const conditions = {
  target:
    values.command !== undefined
      ? { command: values.command }
      : { image, id: docker("image", "inspect", "--format", "{{.Id}}", image), labels: labels() },
  dataset: { sha256: createHash("sha256").update(datasetText).digest("hex"), size: dataset.length },
  timeout: values.timeout,
  memory: values.memory,
  env: values.env,
  batch: values.batch,
};

const done = new Set<number>();
if (existsSync(outputPath)) {
  const [head, ...lines] = readFileSync(outputPath, "utf8").split("\n").filter((line) => line !== "");
  if (JSON.stringify(JSON.parse(head).meta.conditions) !== JSON.stringify(conditions)) {
    console.error(`${outputPath} has the results of other conditions`);
    process.exit(1);
  }
  for (const line of lines) done.add(JSON.parse(line).index);
} else {
  const meta = {
    conditions,
    parallel,
    host: {
      cpu: os.cpus()[0].model,
      cpus: os.cpus().length,
      memory: os.totalmem(),
      os: `${os.type()} ${os.release()} ${os.arch()}`,
      docker: values.command !== undefined ? null : docker("version", "--format", "{{.Server.Version}}"),
    },
    started: new Date().toISOString(),
  };
  writeFileSync(outputPath, JSON.stringify({ meta }) + "\n");
}

class Worker {
  private readonly id: number;
  private starts = 0;
  private name = "";
  private child: ChildProcess | null = null;
  private lines: string[] = [];
  private stderr = "";
  private wake: (() => void) | null = null;

  constructor(id: number) {
    this.id = id;
  }

  private start(): void {
    // The name is new on each start, because the container of a killed tool keeps its name for a while.
    this.name = `redos-experiment-${process.pid}-${this.id}-${this.starts++}`;
    const env = values.env.flatMap((pair) => ["--env", pair]);
    this.child =
      values.command !== undefined
        ? spawn(values.command, {
            shell: true,
            detached: true,
            env: { ...process.env, ...Object.fromEntries(values.env.map((pair) => pair.split(/=(.*)/s, 2))) },
          })
        : spawn("docker", [
            ...["run", "--rm", "--interactive", "--init", "--name", this.name],
            ...["--network", "none", "--cpus", "1", "--memory", values.memory, ...env, image],
          ]);
    this.lines = [];
    this.stderr = "";
    const child = this.child;
    // `node:readline` is not used, because it also splits a line at U+2028 and U+2029, which JSON does not escape.
    let rest = "";
    child.stdout!.setEncoding("utf8");
    child.stdout!.on("data", (chunk: string) => {
      if (this.child !== child) return;
      const lines = (rest + chunk).split("\n");
      rest = lines.pop()!;
      this.lines.push(...lines);
      this.wake?.();
    });
    child.stderr!.on("data", (chunk) => {
      if (this.child === child) this.stderr = (this.stderr + chunk).slice(-2000);
    });
    child.on("exit", () => {
      if (this.child !== child) return;
      this.child = null;
      this.wake?.();
    });
    child.stdin!.on("error", () => {});
  }

  stop(): void {
    const child = this.child;
    if (child === null) return;
    this.child = null;
    if (values.command !== undefined) {
      try {
        process.kill(-child.pid!, "SIGKILL");
      } catch {}
    } else {
      try {
        docker("kill", this.name);
      } catch {}
      child.kill("SIGKILL");
    }
  }

  // Waits for a line of the tool: it returns `null` when the tool exits, and `undefined` when the time is over.
  private async line(limit: number): Promise<string | null | undefined> {
    const end = performance.now() + limit;
    while (this.lines.length === 0) {
      if (this.child === null) return null;
      const rest = end - performance.now();
      if (rest <= 0) return undefined;
      let timer: NodeJS.Timeout;
      await new Promise<void>((resolve) => {
        this.wake = resolve;
        timer = setTimeout(resolve, rest);
      });
      clearTimeout(timer!);
      this.wake = null;
    }
    return this.lines.shift();
  }

  // Checks the patterns by a request: a tool of batches takes them all in a request, and another tool takes one.
  // The time of a pattern of a batch is the time of the batch over the number of its patterns.
  async check(items: Item[]): Promise<object[]> {
    if (this.child === null) {
      this.start();
      const ready = await this.line(StartTimeout);
      if (typeof ready !== "string" || JSON.parse(ready).ready !== true) {
        this.stop();
        throw new Error(`the tool does not start: ${this.stderr}`);
      }
      if ((JSON.parse(ready).batch === true) !== (batch !== undefined)) {
        this.stop();
        throw new Error("--batch is for a tool of batches, and such a tool needs it");
      }
    }
    const patterns = items.map((item) => ({ source: item.source, flags: item.flags ?? "" }));
    const start = performance.now();
    this.child!.stdin!.write(JSON.stringify(batch !== undefined ? { patterns } : patterns[0]) + "\n");
    const line = await this.line(timeout * items.length);
    const time = (performance.now() - start) / items.length;
    if (line === undefined) {
      this.stop();
      return items.map(() => ({ status: "timeout", time }));
    }
    if (line === null) return items.map(() => ({ status: "crash", time, message: this.stderr }));
    const results: object[] = batch !== undefined ? JSON.parse(line).results : [JSON.parse(line)];
    return results.map((result) => ({ ...result, time }));
  }
}

const counts: Record<string, number> = {};
const started = Date.now();
let next = 0;
let finished = 0;
const report = (): void => {
  const seconds = Math.round((Date.now() - started) / 1000);
  console.error(`${done.size}/${size} ${seconds}s ${JSON.stringify(counts)}`);
};
const workers = Array.from({ length: parallel }, (_, id) => new Worker(id));
process.on("SIGINT", () => {
  for (const worker of workers) worker.stop();
  process.exit(130);
});
try {
  await Promise.all(
    workers.map(async (worker) => {
      for (;;) {
        const indices: number[] = [];
        while (indices.length < (batch ?? 1) && next < size) {
          const index = next++;
          if (!done.has(index)) indices.push(index);
        }
        if (indices.length === 0) break;
        const results = (await worker.check(indices.map((index) => dataset[index]))) as { status: string }[];
        for (const [i, result] of results.entries()) {
          appendFileSync(outputPath, JSON.stringify({ index: indices[i], ...result }) + "\n");
          done.add(indices[i]);
          counts[result.status] = (counts[result.status] ?? 0) + 1;
          if (++finished % 100 === 0) report();
        }
      }
    }),
  );
} finally {
  for (const worker of workers) worker.stop();
}
report();
