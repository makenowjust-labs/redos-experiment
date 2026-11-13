// `SCSLRE_OPTIONS` has the options of the analysis in JSON.
import { analyse } from "scslre";

import { serve } from "./wrapper.mjs";

const options = JSON.parse(process.env.SCSLRE_OPTIONS ?? "{}");

const range = (quantifier) => [quantifier.start, quantifier.end];

const quantifiersOf = (report) => {
  switch (report.type) {
    case "Trade":
      return [range(report.startQuant), range(report.endQuant)];
    case "Self":
      return [range(report.quant), range(report.parentQuant)];
    case "Move":
      return [range(report.quant)];
  }
};

await serve((source, flags) => {
  const { reports } = analyse({ source, flags }, options);
  if (reports.length === 0) return { status: "safe" };
  return {
    status: "vulnerable",
    complexity: { type: reports.some((report) => report.exponential) ? "exponential" : "polynomial" },
    reports: reports.map((report) => ({
      type: report.type,
      exponential: report.exponential,
      character: report.character.pick,
      quantifiers: quantifiersOf(report),
    })),
  };
});
