// Wraps `recheck agent`.
// `RECHECK_PARAMETERS` has the parameters of the checks in JSON.
import { spawn } from "node:child_process";

import { lines, serve } from "./wrapper.mjs";

const parameters = JSON.parse(process.env.RECHECK_PARAMETERS ?? "{}");

const agent = spawn("java", ["-jar", process.env.RECHECK_JAR ?? "/recheck.jar", "agent"], {
  stdio: ["pipe", "pipe", "inherit"],
});
agent.on("exit", (code) => process.exit(code ?? 1));
const responses = lines(agent.stdout);

const request = async (method, params) => {
  agent.stdin.write(JSON.stringify({ jsonrpc: "2.0+push", id: 0, method, params }) + "\n");
  for (;;) {
    const { value } = await responses.next();
    const response = JSON.parse(value);
    if (response.result !== undefined || response.error !== undefined) return response;
  }
};

const resultOf = ({ result, error }) => {
  if (error !== undefined) return { status: "error", message: JSON.stringify(error) };
  // The runtime-attack verification, when the parameters enable it, runs the attack strings on the engine of the image:
  // `runtimeAttack` is the run of the reported one, and `refuted` is the number of the ones that it refuted.
  const fields = {
    checker: result.checker ?? undefined,
    runtimeAttack: result.runtimeAttack ?? undefined,
    refuted: result.refutedAttacks?.length || undefined,
  };
  switch (result.status) {
    case "vulnerable":
      return {
        status: "vulnerable",
        complexity: { type: result.complexity.type, degree: result.complexity.degree },
        attack: {
          pumps: result.attack.pumps.map(({ prefix, pump }) => ({ prefix, pump })),
          suffix: result.attack.suffix,
        },
        ...fields,
      };
    case "safe":
      return { status: "safe", complexity: { type: result.complexity.type }, ...fields };
    default:
      // The timeout here is the one of the parameters, while the runner has its own one.
      return {
        status: result.error.kind === "timeout" ? "timeout" : "error",
        message: result.error.message ?? result.error.kind,
        ...fields,
      };
  }
};

await request("ping", {});
await serve(async (source, flags) => resultOf(await request("check", { source, flags, params: parameters })));
agent.kill();
