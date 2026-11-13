import safe from "safe-regex";

import { serve } from "./wrapper.mjs";

await serve((source, flags) => {
  // The tool reports an invalid pattern as not safe, so the pattern is compiled first.
  const regex = new RegExp(source, flags);
  return { status: safe(regex) ? "safe" : "vulnerable" };
});
