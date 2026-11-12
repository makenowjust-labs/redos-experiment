# benchmark

> Runs ReDoS detection tools on a dataset of patterns under the same conditions.

Each tool is built into a Docker image whose entry point reads patterns and writes results.
The runner starts the images, gives the patterns one by one, stops a tool that exceeds the time limit, and records the results with the conditions of the run.

## Requirements

- Docker with BuildKit
- Node.js 24 or later

The images are built for `linux/amd64`.

## Tools

| Tool                      | Source                                                                                              |
| ------------------------- | --------------------------------------------------------------------------------------------------- |
| `codeql`                  | The CodeQL bundle of [github/codeql-action](https://github.com/github/codeql-action/releases)       |
| `lara`                    | The artifact of the paper at [10.5281/zenodo.21485406](https://doi.org/10.5281/zenodo.21485406)     |
| `rat`                     | [phreppo/rat](https://github.com/phreppo/rat)                                                       |
| `recheck`                 | A release of [makenowjust-labs/recheck](https://github.com/makenowjust-labs/recheck)                |
| `redos-detector`          | The npm package [redos-detector](https://www.npmjs.com/package/redos-detector)                      |
| `redoshunter`             | [yetingli/ReDoSHunter](https://github.com/yetingli/ReDoSHunter)                                     |
| `regex-matching-analyzer` | [minamide-group/regex-matching-analyzer](https://github.com/minamide-group/regex-matching-analyzer) |
| `regex-static-analysis`   | [NicolaasWeideman/RegexStaticAnalysis](https://github.com/NicolaasWeideman/RegexStaticAnalysis)     |
| `regexploit`              | [doyensec/regexploit](https://github.com/doyensec/regexploit)                                       |
| `regulator`               | [ucsb-seclab/regulator-dynamic](https://github.com/ucsb-seclab/regulator-dynamic)                   |
| `rengar`                  | [wangx1nyi/Rengar](https://github.com/wangx1nyi/Rengar)                                             |
| `rescue`                  | [2bdenny/ReScue](https://github.com/2bdenny/ReScue)                                                 |
| `revealer`                | [cuhk-seclab/Revealer](https://github.com/cuhk-seclab/Revealer)                                     |
| `rxxr2`                   | The archive of the authors, from [davisjam/redos-detector-backup](https://github.com/davisjam/redos-detector-backup) |
| `safe-regex`              | The npm package [safe-regex](https://www.npmjs.com/package/safe-regex)                              |
| `scslre`                  | The npm package [scslre](https://www.npmjs.com/package/scslre)                                      |

The commit of a tool with a repository is the commit of its submodule in `dep`.
An image is built from this commit of the upstream repository, not from the files of the working tree.
The version of a tool of an npm package is the one of `package.json` and `package-lock.json` in its directory.
The Dockerfile of another tool downloads its sources and checks them by SHA-256.
The base images are pinned by their digests.

## Usage

Build the image of a tool.
The last line of the output is the name of the image.

```console
$ node build.ts regex-static-analysis
redos-experiment/regex-static-analysis:b4d6541662a5
$ node build.ts recheck:4.6.2
redos-experiment/recheck:4.6.2
```

To build an image of `recheck` from a jar that is not released, give the directory of `recheck.jar` as the build context `jar`.

```console
$ node build.ts recheck:my-branch --build-arg JAR=local --build-context jar=/path/to/directory
```

Run an image on a dataset.

```console
$ node run.ts ../data/redos-regexp.json ../result/regex-static-analysis.jsonl \
    --image redos-experiment/regex-static-analysis:b4d6541662a5 --timeout 10 --parallel 8
```

Print the summary of results.

```console
$ node summary.ts ../result/regex-static-analysis.jsonl ../result/recheck.jsonl
```

Measure the attack strings of a result on an engine.

```console
$ node build.ts node
redos-experiment/node:26.13.0
$ node run.ts ../data/redos-regexp.json ../result/recheck.attack.jsonl \
    --image redos-experiment/node:26.13.0 --attacks ../result/recheck.jsonl --timeout 120 --parallel 8
```

### Options of the runner

| Option                | Description                                                      | Default |
| --------------------- | ---------------------------------------------------------------- | ------- |
| `--image <ref>`       | The image of the tool.                                           |         |
| `--command <command>` | A command to run instead of an image, for development.           |         |
| `--timeout <seconds>` | The wall-clock limit of a pattern.                               | `10`    |
| `--parallel <n>`      | The number of the tool processes.                                | `1`     |
| `--memory <size>`     | The memory limit of a tool process.                              | `4g`    |
| `--env <KEY=VALUE>`   | An environment variable of the tool. It can be given many times. |         |
| `--batch <n>`         | The number of the patterns of a request, for a tool of batches.  |         |
| `--attacks <result>`  | The result of a tool whose attack strings an engine takes.       |         |
| `--limit <n>`         | The number of the patterns to run from the head of the dataset.  |         |

A tool process runs with one CPU and without the network.
Running the runner again with the same conditions continues from the patterns that the output already has.

### Environment variables of the tools

| Tool             | Variable                 | Description                                                                       |
| ---------------- | ------------------------ | --------------------------------------------------------------------------------- |
| `lara`           | `LARA_MODE`              | The mode of the detection: `both` (default), `polynomial` or `exponential`.       |
| `rat`            | `RAT_SEMANTICS`          | The semantics of the analysis: `match` (default) or `fullmatch`.                  |
| `recheck`        | `RECHECK_PARAMETERS`     | The parameters of the checks in JSON.                                             |
| `redos-detector` | `REDOS_DETECTOR_OPTIONS` | The options of `isSafePattern` in JSON.                                           |
| `redoshunter`    | `REDOSHUNTER_LANGUAGE`   | The language whose engine validates the attack strings: `js` (default) or `java`. |
| `regulator`      | `REGULATOR_FTIME`        | The time of the fuzzing in milliseconds (the default of the driver: 240000).      |
| `regulator`      | `REGULATOR_PTIME`        | The time of the pumping in milliseconds (the default of the driver: 240000).      |
| `regulator`      | `REGULATOR_LENGTH`       | The length of the strings of the fuzzing (the default of the driver: 200).        |
| `regulator`      | `REGULATOR_WIDTH`        | The width of the characters of the fuzzing in bytes (the default of the driver: 1). |
| `regulator`      | `REGULATOR_BINARY_SEARCH` | `false` stops the search of the length of 10 seconds after the measurement that decides the result. |
| `scslre`         | `SCSLRE_OPTIONS`         | The options of `analyse` in JSON.                                                 |
| JVM tools        | `JAVA_TOOL_OPTIONS`      | The options of the JVM.                                                           |

## Dataset

A dataset is a JSON file of an array, or a JSON Lines file, of objects with `source` and `flags`.

## Result

A result is a JSON Lines file.
The first line has `meta`: the conditions of the run (the image with its ID and labels, the SHA-256 of the dataset, the time limit, the memory limit and the environment variables), the number of the processes, the host, and the start time.
Each of the other lines is the result of a pattern.

| Field        | Description                                                                                                     |
| ------------ | --------------------------------------------------------------------------------------------------------------- |
| `index`      | The index of the pattern in the dataset.                                                                        |
| `status`     | `vulnerable`, `safe`, `timeout`, `error` (the tool rejected the pattern) or `crash` (the tool process exited).  |
| `time`       | The time from the request to the response in milliseconds.                                                      |
| `complexity` | `{"type": "exponential"}` or `{"type": "polynomial", "degree": n}`, as far as the tool reports it.              |
| `attack`     | `{"pumps": [{"prefix": ..., "pump": ...}, ...], "suffix": ...}` or `{"string": ...}`.                           |
| `message`    | The message of an error.                                                                                        |

A tool may add its own fields.

## Engines

The tools do not agree on what a vulnerable pattern is, so an engine measures what their attack strings do on a real implementation.
With `--attacks`, the runner gives an engine each pattern that the result has an attack string of, with the attack string.

The engine `node` matches the pattern by `RegExp.prototype.exec` on V8, each match in its own process.
The attack string of the repeat count `n` is the prefixes and `n` times the pumps in their order, and the suffix.
The repeat count is doubled from 1 until the string is longer than the maximum length or a match is slow.
For an attack string without a suffix, it tries no suffix, `\0` and a line feed in this order.

| Variable               | Description                                              | Default  |
| ---------------------- | -------------------------------------------------------- | -------- |
| `ATTACK_MAX_LENGTH`    | The maximum length of an attack string in characters.    | `100000` |
| `ATTACK_SLOW_TIME`     | The time of a slow match in milliseconds.                | `1000`   |
| `ATTACK_MATCH_TIMEOUT` | The time to stop a match in milliseconds.                | `10000`  |

The result of a pattern has the following fields.

| Field    | Description                                                                                                     |
| -------- | --------------------------------------------------------------------------------------------------------------- |
| `status` | `slow` when a match was slow or was stopped, `fast` otherwise, or `error` when V8 does not compile the pattern. |
| `series` | For each suffix that was tried, `suffix` and `sizes`: `repeat`, `length` and `time` of each match in milliseconds, where the time of a stopped match is `null`. |

## Notes on the tools

- `codeql`: it takes batches, and each pattern is written as a regular expression literal in its own file. The result is the findings of `js/redos` and of the terms under `js/polynomial-redos` without the data flow, so it has no degree and no suffix. The parser of its extractor reports errors on some valid patterns and analyses them anyway, so `errors` of the result has them. The prefix and the pump of a pattern can differ with the other patterns of the batch. The terms of CodeQL do not let the CLI be distributed, so the image must not be published.
- `lara`: the flags are not used. It analyses a pattern as a match of the whole input, so it does not report a pattern that is slow only in a search. It reports a prefix and a pump without a suffix and without a degree. The wrapper runs its command for each pattern.
- `rat`: it reports exponential patterns only. The pattern is given as `/source/flags` with the flags `g`, `i`, `m` and `s` only, as its parser reads other flags as a part of the pattern. Its command reports a pattern as safe when it finds no attack string of printable characters, and `.` matches line terminators. The wrapper runs its command for each pattern.
- `regex-static-analysis`: the flags are not used. The degree of the analysis is the degree of ambiguity, so the result has it plus one.
- `regexploit`: it is called in the way of `regexploit --flavour js`, so the flags are not used and only the results of cubic or more are reported. Its starriness over 10 is recorded as exponential.
- `regulator`: it runs the driver of the repository with `driver.patch`, which fixes two exceptions of the driver. It builds its fuzzer with Node.js 15.0.0-pre (V8 8.3), and measures the attack strings on Node.js of the image. A pattern is vulnerable when an attack string of at most 1,000,000 characters takes 10 seconds. A pattern that V8 does not compile with irregexp (a plain string, or a flag other than `gimsuy`) is recorded as `error`, and a pattern that is slow on the seed of the fuzzer, a string of `a`, as `timeout`. The fuzzing is bounded by time, so results differ between runs, and the degree is a rounded exponent of a regression. With the default times of the driver, a pattern takes 4 to 8 minutes, so the time limit of the runner has to be longer.
- `rengar`: the flags are not used. It runs on Java 17 with the preview features, and a hit of its own time limit of 15 seconds is recorded as `timeout`. Its validator takes the type name `SLQ` to validate an attack string by a search, while `SLQPattern` is named `POLS`, so the tool as published validates every attack string by a match of the whole input and reports most patterns that are polynomial only in a search as safe. The Dockerfile fixes this name in the validator, and this is the only change to the tool.
- `rescue`: the flags are not used. Its genetic algorithm takes random numbers without a seed, so results differ between runs.
- `revealer`: the flags are not used. It reports a pattern only when an attack string of at most 128 characters takes 100000 steps on its regex engine, so most polynomial patterns are reported as safe. Its analysis depends on the iteration order of hash sets of objects, so results differ between runs.
- `regex-matching-analyzer`: the flags `g` and `m` are removed, as its parser does not accept them. The sources of the tool are compiled together with the wrapper instead of running its sbt build.
- `redoshunter`: the jar of its Maven build has the classes of a dependency (`dk.brics.automaton`) over its own modified classes, so the image puts the classes of the tool first on the class path.
- `recheck`: a timeout by its own `timeout` parameter is also recorded as `timeout`.
- `redos-detector`: it reports a pattern as not safe when the check hits one of its limits. `reason` of the result is the limit, and a hit of its own time limit is recorded as `timeout`.
- `rxxr2`: it reports exponential patterns only. It is the original archive of the authors, built into bytecode by its `build.sh`. The flags `i`, `m` and `s` are given and the others are removed, as its parser does not accept them. A non-ASCII character is an error, and `.` matches line terminators. The wrapper runs its command for each pattern.
- `safe-regex`: it reports an invalid pattern as not safe, so the wrapper compiles the pattern first and records the failure as `error`.
- `scslre`: it reports no attack string. `reports` of the result has the type, the character and the positions of the quantifiers of each report.

## Adding a tool

Add `tools/<tool>/Dockerfile`.
The entry point of the image writes `{"ready": true}` on a line of the standard output, and then, for each line `{"source": ..., "flags": ...}` of the standard input, writes the result on a line.
A tool on the JVM can extend `tools/wrapper/Wrapper.scala`, and a tool on Node.js can use `tools/wrapper/wrapper.mjs`.

A tool with a large cost for each run can take batches instead.
It writes `{"ready": true, "batch": true}`, and then, for each line `{"patterns": [{"source": ..., "flags": ...}, ...]}`, writes `{"results": [...]}` with the results in the same order.
The time limit of a batch is the time limit of a pattern times the number of its patterns, and the time of each pattern is the time of the batch over this number.
