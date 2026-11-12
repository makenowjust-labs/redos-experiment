// Measures the time of the matches of a pattern against its attack string of growing sizes on V8.
//
// The attack string of the repeat count `n` is the prefixes and `n` times the pumps in their order, and the suffix.
// The repeat count is doubled from 1 until the string is longer than `ATTACK_MAX_LENGTH` characters (default: 100000),
// or a match takes `ATTACK_SLOW_TIME` milliseconds or more (default: 1000).
// A match that does not end in `ATTACK_MATCH_TIMEOUT` milliseconds (default: 10000) is stopped.
// The status is `slow` when a match took `ATTACK_SLOW_TIME` or was stopped, and `fast` otherwise.
import { spawn } from "node:child_process";
import path from "node:path";

import { serve } from "./wrapper.mjs";

const maxLength = Number(process.env.ATTACK_MAX_LENGTH ?? 100000);
const slowTime = Number(process.env.ATTACK_SLOW_TIME ?? 1000);
const matchTimeout = Number(process.env.ATTACK_MATCH_TIMEOUT ?? 10000);

// The suffixes to try for an attack string of a tool that reports none.
const suffixes = ["", "\u0000", "\n"];

const script = path.join(import.meta.dirname, "match.mjs");

// Runs a match in its own process, because a match on V8 cannot be stopped from its thread.
// It returns the time in milliseconds, or `null` when the match was stopped.
const match = (source, flags, input) =>
  new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [script], { stdio: ["pipe", "pipe", "inherit"] });
    let stopped = false;
    let stdout = "";
    const timer = setTimeout(() => {
      stopped = true;
      child.kill("SIGKILL");
    }, matchTimeout);
    child.stdout.on("data", (chunk) => {
      stdout += chunk;
    });
    child.on("exit", (code, signal) => {
      clearTimeout(timer);
      if (stopped) resolve(null);
      else if (code === 0) resolve(Number(stdout));
      else reject(new Error(`the match exited with ${code ?? signal}`));
    });
    child.stdin.on("error", () => {});
    child.stdin.end(JSON.stringify({ source, flags, input }));
  });

const measure = async (source, flags, inputs) => {
  const sizes = [];
  for (const { repeat, input } of inputs) {
    const time = await match(source, flags, input);
    sizes.push({ repeat, length: input.length, time });
    if (time === null || time >= slowTime) return { slow: true, sizes };
  }
  return { slow: false, sizes };
};

function* inputsOf(pumps, suffix) {
  const grows = pumps.some(({ pump }) => pump !== "");
  for (let repeat = 1; ; repeat *= 2) {
    const input = pumps.map(({ prefix, pump }) => prefix + pump.repeat(repeat)).join("") + suffix;
    if (input.length > maxLength) return;
    yield { repeat, input };
    if (!grows) return;
  }
}

await serve(async (source, flags, { attack }) => {
  new RegExp(source, flags);
  if (attack.string !== undefined) {
    const { slow, sizes } = await measure(source, flags, [{ repeat: 1, input: attack.string }]);
    return { status: slow ? "slow" : "fast", series: [{ sizes }] };
  }
  const series = [];
  for (const suffix of attack.suffix !== undefined ? [attack.suffix] : suffixes) {
    const { slow, sizes } = await measure(source, flags, inputsOf(attack.pumps, suffix));
    series.push({ suffix, sizes });
    if (slow) return { status: "slow", series };
  }
  return { status: "fast", series };
});
