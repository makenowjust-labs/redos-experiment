// Usage: node sample-lingua-franca.mts <uniq-regexes-8.json> <size> <output>
//
// Takes a sample of the JavaScript patterns of the corpus of "Why Aren't Regular Expressions a Lingua Franca?"
// (Davis et al., ESEC/FSE 2019, https://doi.org/10.5281/zenodo.3257777), which is under the MIT license.
// `uniq-regexes-8.json` is `data/production-regexes/uniq-regexes-8.json` of its artifact, whose SHA-256 is
// 9f18b28654278e307db48a7dc164379ffdba96364c2a58a0d51a1d425e3fcc4a.
//
// A pattern is a candidate when it was found in a module of npm, it compiles on the running Node.js, and it is not in
// the other datasets of `data`.
// The sample is the same on every run with the same version of Node.js.

import { readFileSync, writeFileSync } from "node:fs";
import path from "node:path";

const [corpusPath, sizeText, outputPath] = process.argv.slice(2);
if (corpusPath === undefined || sizeText === undefined || outputPath === undefined) {
  console.error("Usage: node sample-lingua-franca.mts <uniq-regexes-8.json> <size> <output>");
  process.exit(1);
}

const data = path.resolve(import.meta.dirname, "../../data");
const known = new Set<string>();
for (const name of ["redos-regexp.json", "regexlib-regexp.json", "snort-regexp.json"]) {
  for (const { source } of JSON.parse(readFileSync(path.join(data, name), "utf8"))) known.add(source);
}

const compiles = (source: string): boolean => {
  try {
    new RegExp(source);
    return true;
  } catch {
    return false;
  }
};

const counts = { npm: 0, invalid: 0, known: 0 };
const candidates: { line: number; source: string }[] = [];
for (const [line, text] of readFileSync(corpusPath, "utf8").split("\n").entries()) {
  if (text === "") continue;
  const { pattern, useCount_registry_to_nModules: registries } = JSON.parse(text);
  if (typeof pattern !== "string" || pattern === "" || registries?.npm === undefined) continue;
  counts.npm++;
  if (!compiles(pattern)) counts.invalid++;
  else if (known.has(pattern)) counts.known++;
  else candidates.push({ line: line + 1, source: pattern });
}

// A linear congruential generator, so that the sample does not depend on the random numbers of the run.
let state = 20261112;
const random = (): number => (state = (Math.imul(state, 1664525) + 1013904223) >>> 0) / 2 ** 32;
const sample = candidates
  .map((candidate) => ({ key: random(), candidate }))
  .sort((a, b) => a.key - b.key)
  .slice(0, Number(sizeText))
  .map(({ candidate }) => candidate)
  .sort((a, b) => a.line - b.line);

const items = sample.map(({ line, source }) => ({
  package: "lingua-franca",
  version: "",
  path: "data/production-regexes/uniq-regexes-8.json",
  line,
  column: 0,
  source,
  flags: "",
}));
writeFileSync(outputPath, JSON.stringify(items));
console.log(`npm ${counts.npm}, invalid ${counts.invalid}, known ${counts.known}, sample ${items.length}`);
