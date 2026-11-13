// Runs the command of rat for each pattern.
// `RAT_SEMANTICS` has the semantics of the matching: `match` or `fullmatch`.
import { spawnSync } from "node:child_process";

import { serve } from "./wrapper.mjs";

const options = process.env.RAT_SEMANTICS === undefined ? [] : ["--semantics", process.env.RAT_SEMANTICS];

// The lexer of rat reads `/` and the flags at the end of a pattern as a part of the pattern unless it knows all of
// the flags.
const KnownFlags = "gims";

await serve((source, flags) => {
  const known = [...flags].filter((flag) => KnownFlags.includes(flag)).join("");
  const { error, status, stdout, stderr } = spawnSync("rat", ["--regex", `/${source}/${known}`, ...options], {
    encoding: "utf8",
  });
  // The pattern is too long for an argument, for example.
  if (error !== undefined) throw error;
  // The first line of the output is a separator.
  const output = stdout.replace(/^.*\n/, "");
  if (status === 1) return { status: "error", message: output.trim() };
  if (status !== 0) {
    // The runner records it as a crash.
    process.stderr.write(stderr);
    process.exit(1);
  }

  if (output.startsWith("Analysis timeout expired.")) return { status: "timeout", message: output.trim() };
  const runtime = Number(output.match(/^Runtime\(ms\): (.*)$/m)[1]);
  if (output.startsWith("Exponential ReDoS: false")) return { status: "safe", runtime };
  const field = (name) => output.match(new RegExp(`^  ${name} = '(.*)'$`, "m"))[1];
  return {
    status: "vulnerable",
    complexity: { type: "exponential" },
    attack: { pumps: [{ prefix: field("prefix"), pump: field("pump") }], suffix: field("suffix") },
    runtime,
  };
});
