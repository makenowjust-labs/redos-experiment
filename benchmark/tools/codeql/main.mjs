// This tool answers a batch of patterns per request, because a database of CodeQL has a large fixed cost.
// A request is a line `{"patterns": [{"source": ..., "flags": ...}, ...]}`, and its answer is a line `{"results": [...]}`.
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";

import { lines } from "./wrapper.mjs";

const query = path.join(import.meta.dirname, "redos.ql");

const codeql = (...args) => {
  const { status, signal, stdout, stderr } = spawnSync("codeql", args, {
    encoding: "utf8",
    maxBuffer: Infinity,
    // The extractor skips a file whose lines are longer than 200 characters on average without this.
    env: { ...process.env, CODEQL_EXTRACTOR_JAVASCRIPT_ALLOW_MINIFIED_FILES: "true" },
  });
  if (status !== 0) throw new Error(`codeql ${args[0]} ${args[1]}: ${stderr.trim().slice(-2000) || `signal ${signal}`}`);
  return stdout;
};

// A regular expression literal cannot have a line terminator or a slash as it is.
const escapes = {
  "\n": "\\n",
  "\r": "\\r",
  [String.fromCharCode(0x2028)]: "\\u2028",
  [String.fromCharCode(0x2029)]: "\\u2029",
  "/": "\\/",
};

// Writes the pattern as the body of a regular expression literal.
// `offsets[i]` is the offset in the source of the character at `i` of the body.
const literalOf = (source) => {
  if (source === "") return { body: "(?:)", offsets: [0, 0, 0, 0, 0] };
  let body = "";
  const offsets = [];
  const put = (text, offset) => {
    body += text;
    for (let i = 0; i < text.length; i++) offsets.push(offset);
  };
  for (let i = 0; i < source.length; i++) {
    const escaped = source[i] === "\\" && i + 1 < source.length;
    const char = source[escaped ? i + 1 : i];
    if (escapes[char] !== undefined) {
      put(escapes[char], i);
      if (escaped) offsets[offsets.length - 1] = i + 1;
    } else if (escaped) {
      put("\\", i);
      put(char, i + 1);
    } else {
      put(char, i);
    }
    if (escaped) i++;
  }
  offsets.push(source.length);
  return { body, offsets };
};

// Reverts `escape` of `codeql.util.Strings`, which the library applies to a prefix and a pump.
const unescape = (text) =>
  text.replace(/\\(?:([\\nrt])|u\{([0-9a-fA-F]+)\}|u([0-9a-fA-F]{4}))/g, (_, char, long, short) =>
    char !== undefined ? { "\\": "\\", n: "\n", r: "\r", t: "\t" }[char] : String.fromCodePoint(parseInt(long ?? short, 16)),
  );

const group = (tuples) => {
  const groups = new Map();
  for (const [file, ...rest] of tuples) {
    if (!groups.has(file)) groups.set(file, []);
    groups.get(file).push(rest);
  }
  return groups;
};

const check = (patterns) => {
  const directory = mkdtempSync(path.join(os.tmpdir(), "codeql-"));
  try {
    const sources = path.join(directory, "src");
    mkdirSync(sources);
    const literals = patterns.map(({ source, flags = "" }, index) => {
      // The parser of the extractor recovers from any error, so it does not tell whether a pattern is valid.
      try {
        new RegExp(source, flags);
      } catch (error) {
        return { error: error.message };
      }
      // A file cannot have a lone surrogate.
      if (!source.isWellFormed()) return { error: "the pattern has a lone surrogate" };
      const literal = literalOf(source);
      writeFileSync(path.join(sources, `p${index}.js`), `/${literal.body}/${flags};\n`);
      return literal;
    });

    const database = path.join(directory, "db");
    const output = path.join(directory, "output.bqrs");
    codeql("database", "create", "--language=javascript", `--source-root=${sources}`, "--threads=1", database);
    codeql("query", "run", `--database=${database}`, `--output=${output}`, "--threads=1", query);
    const tables = JSON.parse(codeql("bqrs", "decode", "--format=json", output));
    const roots = group(tables.roots.tuples);
    const fileErrors = group(tables.fileErrors.tuples);
    const patternErrors = group(tables.patternErrors.tuples);
    const findings = group(tables.findings.tuples);

    return literals.map((literal, index) => {
      if (literal.error !== undefined) return { status: "error", message: literal.error };
      const file = `p${index}.js`;
      if (fileErrors.has(file)) return { status: "error", message: fileErrors.get(file).map(([message]) => message).join("; ") };
      if (!roots.has(file)) return { status: "error", message: "the extractor found no regular expression" };
      if (roots.get(file).some(([relevant]) => !relevant)) {
        return { status: "error", message: "the analysis excluded the regular expression" };
      }
      // The body of the literal starts at the second column, and columns start at one.
      const offset = (column) => literal.offsets[column - 2];
      // The parser of the extractor reports errors on some valid patterns, such as `{` as a character.
      // The queries of CodeQL work on the terms that it made anyway, so the errors are kept beside the result.
      const errors = (patternErrors.get(file) ?? [])
        .map(([column, message]) => ({ message, offset: offset(column) }))
        .sort((a, b) => a.offset - b.offset);
      const parse = errors.length === 0 ? {} : { errors };
      if (!findings.has(file)) return { status: "safe", ...parse };

      const found = findings
        .get(file)
        .map(([type, startColumn, endColumn, term, prefix, pump, message]) => ({
          type,
          term,
          start: offset(startColumn),
          end: offset(endColumn + 1),
          prefix: unescape(prefix),
          pump: unescape(pump),
          message,
        }))
        .sort((a, b) => a.type.localeCompare(b.type) || a.start - b.start || a.end - b.end);
      // The first one is exponential if any is, as the findings are in the order of the types.
      const [{ type, prefix, pump }] = found;
      return { status: "vulnerable", complexity: { type }, attack: { pumps: [{ prefix, pump }] }, findings: found, ...parse };
    });
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
};

process.stdout.write(JSON.stringify({ ready: true, batch: true }) + "\n");
for await (const line of lines(process.stdin)) {
  const { patterns } = JSON.parse(line);
  let results;
  try {
    results = check(patterns);
  } catch (error) {
    results = patterns.map(() => ({ status: "error", message: String(error.message) }));
  }
  process.stdout.write(JSON.stringify({ results }) + "\n");
}
