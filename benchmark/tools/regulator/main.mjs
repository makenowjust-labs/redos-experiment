// Runs the driver of Regulator for each pattern.
// `REGULATOR_FTIME` and `REGULATOR_PTIME` are the times of the fuzzing and of the pumping in milliseconds,
// `REGULATOR_LENGTH` is the length of the strings of the fuzzing, and `REGULATOR_WIDTH` is the width of their
// characters in bytes (`1` or `2`).
// Their defaults are the ones of the driver.
// `REGULATOR_BINARY_SEARCH=false` stops the binary search of the length of 10 seconds after its measurement of the
// longest attack string, which decides the result.
import { spawnSync } from "node:child_process";

import { serve } from "./wrapper.mjs";

const Fuzzer = process.env.REGULATOR_FUZZER;

const option = (name, variable) => (process.env[variable] === undefined ? [] : [`--${name}=${process.env[variable]}`]);
const options = [
  ...option("ftime", "REGULATOR_FTIME"),
  ...option("ptime", "REGULATOR_PTIME"),
  ...option("length", "REGULATOR_LENGTH"),
  ...option("width", "REGULATOR_WIDTH"),
];

// The validation of the artifact of the paper takes a time from 9.9 seconds as 10 seconds.
// A measurement without a time is a time-out of the driver.
const Slow = 9900;

await serve((source, flags) => {
  const pattern = Buffer.from(source).toString("base64");

  // The driver does not tell a pattern that the fuzzer does not compile from another failure.
  const flagsOption = flags === "" ? [] : ["--flags", flags];
  const compile = spawnSync(
    Fuzzer,
    ["--bregexp", pattern, ...flagsOption, "--widths", "1", "--count-paths", "--maxpath", "1"],
    { input: "", encoding: "utf8" },
  );
  if (compile.status !== 0) return { status: "error", message: compile.stderr.trim() || `signal ${compile.signal}` };

  // The driver reads the pattern from the standard input when it has no argument of it.
  const driver = spawnSync("python3", ["/wrapper/run.py", "--log=none", `--flags=${flags}`, ...options], {
    input: pattern + "\n",
    encoding: "utf8",
    stdio: ["pipe", "pipe", "inherit"],
    detached: true,
    maxBuffer: Infinity,
  });
  // A failure of the driver can leave the processes that it started.
  try {
    process.kill(-driver.pid, "SIGKILL");
  } catch {}
  if (driver.status !== 0) {
    // The runner records it as a crash.
    console.error(`the driver exited with ${driver.status ?? driver.signal}`);
    process.exit(1);
  }

  const result = JSON.parse(driver.stdout.trimEnd().split("\n").at(-1));
  // The fuzzer runs the pattern on its seed of `a` without a limit, so it does not end for a pattern that is slow on
  // this seed.
  if (result.exit === 7) return { status: "timeout", message: "the fuzzer wrote no witness" };
  const { witness, pump_pos: start, times } = result;
  const end = start + result.pump_len;
  // `length` is the length of 10 seconds of the binary search.
  const fields = { class: result.class, times, length: result.length };
  if (!times.some(([, time]) => time === null || time >= Slow)) return { status: "safe", ...fields };
  const polynomial = result.class === "POLYNOMIAL";
  const pumps = [{ prefix: witness.slice(0, start), pump: witness.slice(start, end) }];
  return {
    status: "vulnerable",
    complexity: polynomial ? { type: "polynomial", degree: result.degree } : { type: "exponential" },
    attack: { pumps, suffix: witness.slice(end) },
    ...fields,
  };
});
