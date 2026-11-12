// Usage: node build.ts <tool>[:<tag>] [<docker build options>...]
//
// Builds the image `redos-experiment/<tool>:<tag>` from `tools/<tool>/Dockerfile`.
// A tool with a submodule is built from the commit of the submodule that `HEAD` has, and the tag is this commit
// unless it is given.
// The tag of a tool with `package.json` is the version of the package unless it is given.
// The tag of `recheck` is the version of the release to take.

import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

const sources: Record<string, string> = {
  redoshunter: "dep/ReDoSHunter",
  "regex-matching-analyzer": "dep/regex-matching-analyzer",
  "regex-static-analysis": "dep/RegexStaticAnalysis",
  rescue: "dep/ReScue",
};

const [target, ...options] = process.argv.slice(2);
if (target === undefined) {
  console.error("Usage: node build.ts <tool>[:<tag>] [<docker build options>...]");
  process.exit(1);
}
const [tool, givenTag] = target.split(":");

const root = path.resolve(import.meta.dirname, "..");
const directory = path.join(root, "benchmark/tools", tool);
const git = (...args: string[]): string => execFileSync("git", args, { cwd: root, encoding: "utf8" }).trim();

// The runner records the labels of this prefix in the results.
const label = (name: string, value: string): string[] => ["--label", `codes.quine.labs.redos-experiment.${name}=${value}`];

const dirty = git("status", "--porcelain", "--", "benchmark") === "" ? "" : "-dirty";
const args = label("revision", git("rev-parse", "HEAD") + dirty);
let tag = givenTag;
const source = sources[tool];
if (source !== undefined) {
  const url = git("config", "--file", ".gitmodules", `submodule.${source}.url`).replace(/(\.git)?$/, ".git");
  const commit = git("rev-parse", `HEAD:${source}`);
  tag ??= commit.slice(0, 12);
  args.push("--build-arg", `SOURCE=${url}#${commit}`, ...label("tool-source", url), ...label("tool-revision", commit));
} else if (existsSync(path.join(directory, "package.json"))) {
  const version: string = JSON.parse(readFileSync(path.join(directory, "package.json"), "utf8")).dependencies[tool];
  tag ??= version;
  args.push(...label("tool-version", version));
} else if (tool === "recheck" && tag !== undefined) {
  args.push("--build-arg", `VERSION=${tag}`, ...label("tool-version", tag));
}
if (tag === undefined) {
  console.error(`${tool} needs a tag`);
  process.exit(1);
}

const image = `redos-experiment/${tool}:${tag}`;
execFileSync(
  "docker",
  [
    ...["build", "--platform", "linux/amd64", "--tag", image],
    ...["--build-context", `wrapper=${path.join(root, "benchmark/tools/wrapper")}`],
    ...args,
    ...options,
    directory,
  ],
  { stdio: "inherit" },
);
console.log(image);
