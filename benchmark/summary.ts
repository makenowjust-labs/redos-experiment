// Usage: node summary.ts <result>...
//
// Prints the number of the patterns of each status and the times of each result.
// With several results, it also prints how many patterns each pair of the results reports as vulnerable together.

import { readFileSync } from "node:fs";
import path from "node:path";

type Record_ = { index: number; status: string; time: number };

const results = process.argv.slice(2).map((file) => {
  const [, ...lines] = readFileSync(file, "utf8").split("\n").filter((line) => line !== "");
  const records: Record_[] = lines.map((line) => JSON.parse(line));
  return { name: path.basename(file).replace(/\.jsonl$/, ""), records };
});
if (results.length === 0) {
  console.error("Usage: node summary.ts <result>...");
  process.exit(1);
}

const statuses = [...new Set(results.flatMap(({ records }) => records.map((record) => record.status)))].sort();
const seconds = (ms: number): string => (ms / 1000).toFixed(3);

console.table(
  Object.fromEntries(
    results.map(({ name, records }) => {
      const times = records.map((record) => record.time).sort((a, b) => a - b);
      const total = times.reduce((sum, time) => sum + time, 0);
      return [
        name,
        {
          patterns: records.length,
          ...Object.fromEntries(statuses.map((status) => [status, records.filter((r) => r.status === status).length])),
          "total (s)": seconds(total),
          "mean (s)": seconds(total / times.length),
          "median (s)": seconds(times[Math.floor(times.length / 2)]),
          "max (s)": seconds(times[times.length - 1]),
        },
      ];
    }),
  ),
);

if (results.length > 1) {
  const vulnerable = results.map(
    ({ records }) => new Set(records.filter((record) => record.status === "vulnerable").map((record) => record.index)),
  );
  console.table(
    Object.fromEntries(
      results.map(({ name }, i) => [
        name,
        Object.fromEntries(
          results.map((other, j) => [other.name, [...vulnerable[i]].filter((index) => vulnerable[j].has(index)).length]),
        ),
      ]),
    ),
  );
}
