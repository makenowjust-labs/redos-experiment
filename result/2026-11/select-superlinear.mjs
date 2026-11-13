// Usage: node select-superlinear.mjs <result> <attack result> <output>
//
// Writes the records of the result of a tool whose attack strings are `superlinear` on the engine, as a result that the
// runner takes with `--attacks`.
// A match is measured once, so a single slow match can make a linear growth look superlinear.
// These attack strings are measured again into `<tool>.attack2.jsonl`, and the analysis takes them as `superlinear` only
// when the second measurement agrees.

import { writeFileSync } from "node:fs";

import { read, verdictOf } from "./verdict.mjs";

const [resultPath, attackPath, outputPath] = process.argv.slice(2);
const result = read(resultPath);
const attack = read(attackPath);
const lines = [result.head];
for (const [index, record] of attack.records) {
  if (verdictOf(record) === "superlinear") lines.push(JSON.stringify(result.records.get(index)));
}
writeFileSync(outputPath, lines.join("\n") + "\n");
console.log(lines.length - 1);
