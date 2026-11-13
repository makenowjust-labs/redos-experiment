// Runs `scan.bin` of RXXR2 for each pattern.
import { spawnSync } from "node:child_process";
import { writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";

import { serve } from "./wrapper.mjs";

// RXXR2 reads the patterns from a file.
const input = path.join(os.tmpdir(), "rxxr2-input.txt");

// The flags that the parser of RXXR2 takes in the meaning of JavaScript.
const KnownFlags = "ims";

// RXXR2 prints a character out of the printable ASCII characters as `\xXX`, and a backslash as it is.
const unescape = (word) =>
  word.replace(/\\x([0-9a-f]{2})/g, (text, hex) => {
    const code = Number.parseInt(hex, 16);
    return code < 0x20 || code > 0x7e ? String.fromCharCode(code) : text;
  });

await serve((source, flags) => {
  if (/[\r\n]/.test(source)) {
    return { status: "error", message: "RXXR2 reads a pattern on a line, so it cannot take a line terminator" };
  }
  const known = [...flags].filter((flag) => KnownFlags.includes(flag)).join("");
  writeFileSync(input, `/${source}/${known}\n`);
  const { error, stdout, stderr } = spawnSync("/tool/ocamlrun", ["/tool/scan.bin", "-i", input], { encoding: "utf8" });
  if (error !== undefined) throw error;
  // The first two lines are the number and the input.
  const lines = stdout.split("\n");
  const field = (index, name) => {
    const line = lines[index] ?? "";
    return line.startsWith(`${name}: `) ? line.slice(name.length + 2) : null;
  };

  // The exit status is not checked, as RXXR2 fails in printing the summary when it cannot parse the pattern.
  const parse = field(2, "PARSE");
  if (parse?.startsWith("ERROR")) return { status: "error", message: parse.slice("ERROR {".length, -1) };
  const size = Number(field(3, "SIZE"));
  const pumpable = field(4, "PUMPABLE") === "YES";
  const vulnerable = field(5, "VULNERABLE") ?? "";
  const time = field(!pumpable ? 5 : vulnerable.startsWith("NO") ? 6 : 10, "TIME");
  if (time === null) {
    // The runner records it as a crash.
    process.stderr.write(stderr);
    process.exit(1);
  }
  // The time of RXXR2 is in seconds.
  const runtime = Math.round(Number(time.replace(" (s)", "")) * 1e6) / 1e3;

  if (!pumpable) return { status: "safe", size, pumpable, runtime };
  const interrupted = vulnerable.includes("INTERRUPTED");
  const pruned = vulnerable.includes("PRUNED");
  if (vulnerable.startsWith("NO")) return { status: "safe", size, pumpable, interrupted, pruned, runtime };
  return {
    status: "vulnerable",
    complexity: { type: "exponential" },
    attack: {
      pumps: [{ prefix: unescape(field(7, "PREFIX")), pump: unescape(field(8, "PUMPABLE")) }],
      suffix: unescape(field(9, "SUFFIX")),
    },
    size,
    pumpable,
    interrupted,
    pruned,
    runtime,
  };
});
