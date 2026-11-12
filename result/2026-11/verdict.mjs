// The verdict of a measurement of an attack string on the engine.
//
// It is `slow` when the engine reports so, `superlinear` when it is not slow but its time grows with an exponent of 1.3
// or more, and `fast` otherwise.
// The exponent is the median of the slopes between every two matches, of the logarithm of the time on the logarithm of
// the length, on the matches of 2048 characters or more that took 1 millisecond or more, and it needs three such
// matches.
// A match is measured once, and the median keeps a single match that was slow by chance from deciding the exponent.
// V8 runs a pattern differently on a short input, so the shorter matches are not used.
// The exponents of the attack strings of all the tools that are not slow gather from 1.6 to 2.2, and few are between
// 1.0 and 1.4, so 1.3 separates the growing ones from the noise.

import { readFileSync } from "node:fs";

export const read = (file) => {
  const [head, ...lines] = readFileSync(file, "utf8").split("\n").filter((line) => line !== "");
  const records = new Map(lines.map((line) => JSON.parse(line)).map((record) => [record.index, record]));
  return { head, meta: JSON.parse(head).meta, records };
};

export const exponentOf = (sizes) => {
  const points = sizes
    .filter(({ length, time }) => time !== null && length >= 2048 && time >= 1)
    .map(({ length, time }) => [Math.log(length), Math.log(time)]);
  if (points.length < 3) return null;
  const slopes = [];
  for (const [i, [x1, y1]] of points.entries()) {
    for (const [x2, y2] of points.slice(i + 1)) slopes.push((y2 - y1) / (x2 - x1));
  }
  slopes.sort((a, b) => a - b);
  const middle = Math.floor(slopes.length / 2);
  return slopes.length % 2 === 1 ? slopes[middle] : (slopes[middle - 1] + slopes[middle]) / 2;
};

export const verdictOf = (record) => {
  if (record === undefined) return null;
  if (record.status !== "fast") return record.status;
  const exponents = record.series.map(({ sizes }) => exponentOf(sizes)).filter((exponent) => exponent !== null);
  return exponents.some((exponent) => exponent >= 1.3) ? "superlinear" : "fast";
};
