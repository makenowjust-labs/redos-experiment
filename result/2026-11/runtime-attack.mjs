// Usage: node runtime-attack.mjs <dataset> <result directory> <base tool> <tool with the runtime-attack verification>
//
// Compares the results of recheck without and with the runtime-attack verification, and writes JSON to the standard
// output.
// The verdicts of the attack strings are the ones of `analyze.mjs`, and `long` has the verdicts of the measurement
// with the maximum length of 300000 characters in the directory `long`.

import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

import { read, verdictOf } from "./verdict.mjs";

const [datasetPath, directory, base, runtime] = process.argv.slice(2);
const dataset = JSON.parse(readFileSync(datasetPath, "utf8"));

const load = (tool) => {
  const optional = (file) => (existsSync(file) ? read(file).records : new Map());
  const first = optional(path.join(directory, `${tool}.attack.jsonl`));
  const second = optional(path.join(directory, `${tool}.attack2.jsonl`));
  const long = optional(path.join(directory, "long", `${tool}.attack.jsonl`));
  const verdict = (index) => {
    const verdict = verdictOf(first.get(index));
    if (verdict !== "superlinear" || !second.has(index)) return verdict;
    return verdictOf(second.get(index)) === "fast" ? "fast" : "superlinear";
  };
  const result = read(path.join(directory, `${tool}.jsonl`));
  return { records: result.records, verdict, long: (index) => verdictOf(long.get(index)) };
};
const b = load(base);
const r = load(runtime);

const count = (values) => {
  const counts = {};
  for (const value of values) counts[value] = (counts[value] ?? 0) + 1;
  return counts;
};
const seconds = (tool) => [...tool.records.values()].reduce((sum, record) => sum + record.time, 0) / 1000;

const reported = [...b.records.values()].filter((record) => record.status === "vulnerable").map((record) => record.index);
const outcome = (index) => {
  const record = r.records.get(index);
  if (record.status !== "vulnerable") return record.status;
  return `vulnerable/${r.verdict(index)}`;
};
const describe = (index) => ({
  index,
  source: dataset[index].source,
  flags: dataset[index].flags ?? "",
  base: { verdict: b.verdict(index), long: b.long(index), complexity: b.records.get(index).complexity },
  runtime: {
    status: r.records.get(index).status,
    verdict: r.verdict(index),
    long: r.long(index),
    refuted: r.records.get(index).refuted ?? 0,
    runtimeAttack: r.records.get(index).runtimeAttack?.result,
    message: r.records.get(index).message,
  },
});

console.log(
  JSON.stringify({
    dataset: path.basename(datasetPath),
    seconds: { base: seconds(b), runtime: seconds(r) },
    statuses: { base: count([...b.records.values()].map((x) => x.status)), runtime: count([...r.records.values()].map((x) => x.status)) },
    // What the reports of the base tool become with the verification, by the verdicts of their attack strings.
    outcomes: Object.fromEntries(
      ["slow", "superlinear", "fast"].map((verdict) => [verdict, count(reported.filter((i) => b.verdict(i) === verdict).map(outcome))]),
    ),
    // The same by the verdicts of the measurement of 300000 characters.
    outcomesLong: Object.fromEntries(
      ["slow", "superlinear", "fast"].map((verdict) => [
        verdict,
        count(reported.filter((i) => b.long(i) === verdict).map((i) => r.records.get(i).status)),
      ]),
    ),
    verdicts: {
      runtime: count([...r.records.values()].filter((x) => x.status === "vulnerable").map((x) => r.verdict(x.index))),
      runtimeLong: count([...r.records.values()].filter((x) => x.status === "vulnerable").map((x) => r.long(x.index))),
    },
    refuted: count([...r.records.values()].map((x) => x.refuted ?? 0)),
    // The reports whose attack strings are not confirmed without the verification.
    unconfirmed: reported.filter((i) => b.verdict(i) === "fast").map(describe),
    // The reports that the verification drops though their attack strings are slow in the measurement of 300000
    // characters.
    droppedSlow: reported.filter((i) => b.long(i) === "slow" && r.records.get(i).status !== "vulnerable").map(describe),
    // The patterns that only the run with the verification reports.
    added: [...r.records.values()]
      .filter((x) => x.status === "vulnerable" && b.records.get(x.index).status !== "vulnerable")
      .map((x) => describe(x.index)),
  }),
);
