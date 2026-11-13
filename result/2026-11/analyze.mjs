// Usage: node analyze.mjs <dataset> <result directory> <base tool>
//
// Reads `<tool>.jsonl`, `<tool>.attack.jsonl` and `<tool>.attack2.jsonl` of the directory, and writes JSON to the
// standard output: the counts of each tool, and the patterns where the base tool and the other tools differ.
//
// `verdict.mjs` has the verdict of a measurement of an attack string.
// `<tool>.attack2.jsonl` has the second measurement of the attack strings that are `superlinear` in the first one
// (see `select-superlinear.mjs`), and such an attack string is `superlinear` only when the second one is not `fast`.

import { existsSync, readdirSync, readFileSync } from "node:fs";
import path from "node:path";

import { read, verdictOf } from "./verdict.mjs";

const [datasetPath, directory, base] = process.argv.slice(2);
const dataset = JSON.parse(readFileSync(datasetPath, "utf8"));

const tools = readdirSync(directory)
  .filter((name) => name.endsWith(".jsonl") && !/\.attack2?\.jsonl$/.test(name))
  .map((name) => name.replace(/\.jsonl$/, ""))
  .sort();
const readAll = (suffix) =>
  Object.fromEntries(
    tools
      .filter((tool) => existsSync(path.join(directory, `${tool}${suffix}.jsonl`)))
      .map((tool) => [tool, read(path.join(directory, `${tool}${suffix}.jsonl`))]),
  );
const results = readAll("");
const attacks = readAll(".attack");
const attacks2 = readAll(".attack2");

const statusOf = (tool, index) => results[tool].records.get(index)?.status ?? null;
const verdict = (tool, index) => {
  const first = verdictOf(attacks[tool]?.records.get(index));
  const second = attacks2[tool]?.records.get(index);
  if (first !== "superlinear" || second === undefined) return first;
  return verdictOf(second) === "fast" ? "fast" : "superlinear";
};
const confirmed = (tool, index) => ["slow", "superlinear"].includes(verdict(tool, index));

const count = (values) => {
  const counts = {};
  for (const value of values) counts[value] = (counts[value] ?? 0) + 1;
  return counts;
};

const summary = Object.fromEntries(
  tools.map((tool) => {
    const records = [...results[tool].records.values()];
    const times = records.map((record) => record.time).sort((a, b) => a - b);
    return [
      tool,
      {
        image: results[tool].meta.conditions.target.image,
        statuses: count(records.map((record) => record.status)),
        totalSeconds: times.reduce((sum, time) => sum + time, 0) / 1000,
        medianSeconds: times[Math.floor(times.length / 2)] / 1000,
        hasAttacks: attacks[tool] !== undefined,
        verdicts: attacks[tool] === undefined ? null : count([...attacks[tool].records.keys()].map((i) => verdict(tool, i))),
      },
    ];
  }),
);

const others = tools.filter((tool) => tool !== base);
const describe = (index) => ({
  index,
  source: dataset[index].source,
  flags: dataset[index].flags ?? "",
  base: { status: statusOf(base, index), verdict: verdict(base, index), message: results[base].records.get(index)?.message },
  by: others
    .filter((tool) => statusOf(tool, index) === "vulnerable")
    .map((tool) => ({ tool, verdict: verdict(tool, index), complexity: results[tool].records.get(index).complexity })),
});

const indices = dataset.map((_, index) => index);
const confirmedByAny = indices.filter((index) => tools.some((tool) => confirmed(tool, index)));
const baseVulnerable = (index) => statusOf(base, index) === "vulnerable";

console.log(
  JSON.stringify({
    dataset: path.basename(datasetPath),
    size: dataset.length,
    base,
    tools: summary,
    // The numbers of the patterns that each pair of the tools reports as vulnerable together.
    overlap: Object.fromEntries(
      tools.map((a) => [
        a,
        Object.fromEntries(
          tools.map((b) => [b, indices.filter((i) => statusOf(a, i) === "vulnerable" && statusOf(b, i) === "vulnerable").length]),
        ),
      ]),
    ),
    // The patterns that some tool has a confirmed attack string of, and how many of them each tool reports.
    confirmedByAny: confirmedByAny.length,
    coverage: Object.fromEntries(
      tools.map((tool) => [
        tool,
        {
          reported: confirmedByAny.filter((i) => statusOf(tool, i) === "vulnerable").length,
          confirmedItself: confirmedByAny.filter((i) => confirmed(tool, i)).length,
        },
      ]),
    ),
    // How the reports of each tool hold on the engine.
    // `confirmed` has the reports whose own attack string is confirmed, `fast` the ones whose own attack string is not,
    // and `noAttack` the ones without an attack string.
    // `supported` has the reports of the patterns that some tool has a confirmed attack string of.
    reports: Object.fromEntries(
      tools.map((tool) => {
        const reported = indices.filter((i) => statusOf(tool, i) === "vulnerable");
        return [
          tool,
          {
            reported: reported.length,
            confirmed: reported.filter((i) => confirmed(tool, i)).length,
            fast: reported.filter((i) => verdict(tool, i) === "fast").length,
            noAttack: reported.filter((i) => verdict(tool, i) === null).length,
            supported: reported.filter((i) => confirmedByAny.includes(i)).length,
          },
        ];
      }),
    ),
    // The base tool does not report them, and another tool has a confirmed attack string.
    misses: confirmedByAny.filter((i) => !baseVulnerable(i)).map(describe),
    // The base tool reports them, its attack string is not confirmed, and another tool has a confirmed one.
    weak: confirmedByAny.filter((i) => baseVulnerable(i) && !confirmed(base, i)).map(describe),
    // The base tool reports them, and no tool has a confirmed attack string.
    unconfirmed: indices.filter((i) => baseVulnerable(i) && !confirmedByAny.includes(i)).map(describe),
    // The base tool does not report them, another tool does, and no tool has a confirmed attack string.
    claims: indices
      .filter((i) => !baseVulnerable(i) && !confirmedByAny.includes(i) && others.some((tool) => statusOf(tool, i) === "vulnerable"))
      .map(describe),
  }),
);
