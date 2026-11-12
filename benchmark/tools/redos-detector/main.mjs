// `REDOS_DETECTOR_OPTIONS` has the options of the check in JSON.
import { isSafePattern } from "redos-detector";

import { serve } from "./wrapper.mjs";

const options = JSON.parse(process.env.REDOS_DETECTOR_OPTIONS ?? "{}");

await serve((source, flags) => {
  const result = isSafePattern(source, {
    caseInsensitive: flags.includes("i"),
    dotAll: flags.includes("s"),
    multiLine: flags.includes("m"),
    unicode: flags.includes("u"),
    ...options,
  });
  const fields = { score: result.score, patternDowngraded: result.patternDowngraded };
  if (result.safe) return { status: "safe", ...fields };
  // The tool reports a pattern as not safe when the check hits a limit: `reason` is the limit.
  return { status: result.error === "timedOut" ? "timeout" : "vulnerable", reason: result.error, ...fields };
});
