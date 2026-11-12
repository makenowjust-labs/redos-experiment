// `LARA_MODE` is the mode of the detection: `both` (default), `polynomial` or `exponential`.
import { spawnSync } from "node:child_process";

import { serve } from "./wrapper.mjs";

const mode = process.env.LARA_MODE ?? "both";

const decode = (text) => Buffer.from(text, "base64").toString();

await serve((source) => {
  const pattern = Buffer.from(source).toString("base64");
  const { status, signal, stdout, stderr } = spawnSync("ere", ["detect", "--base64", "--mode", mode, "--pattern", pattern], {
    encoding: "utf8",
  });
  if (status !== 0) return { status: "error", message: stderr.trim() || `signal ${signal}` };
  const result = JSON.parse(stdout);
  if (result.vulnerability === "None") return { status: "safe" };
  return {
    status: "vulnerable",
    complexity: { type: result.vulnerability.toLowerCase() },
    attack: { pumps: [{ prefix: decode(result.prefix), pump: decode(result.infix) }] },
  };
});
