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
| `recheck`                 | A release of [makenowjust-labs/recheck](https://github.com/makenowjust-labs/recheck)                |
| `redos-detector`          | The npm package [redos-detector](https://www.npmjs.com/package/redos-detector)                      |
| `redoshunter`             | [yetingli/ReDoSHunter](https://github.com/yetingli/ReDoSHunter)                                     |
| `regex-matching-analyzer` | [minamide-group/regex-matching-analyzer](https://github.com/minamide-group/regex-matching-analyzer) |
| `regex-static-analysis`   | [NicolaasWeideman/RegexStaticAnalysis](https://github.com/NicolaasWeideman/RegexStaticAnalysis)     |
| `rescue`                  | [2bdenny/ReScue](https://github.com/2bdenny/ReScue)                                                 |
| `safe-regex`              | The npm package [safe-regex](https://www.npmjs.com/package/safe-regex)                              |
| `scslre`                  | The npm package [scslre](https://www.npmjs.com/package/scslre)                                      |

The commit of a tool with a repository is the commit of its submodule in `dep`.
An image is built from this commit of the upstream repository, not from the files of the working tree.
The version of a tool of an npm package is the one of `package.json` and `package-lock.json` in its directory.
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

### Options of the runner

| Option                | Description                                                      | Default |
| --------------------- | ---------------------------------------------------------------- | ------- |
| `--image <ref>`       | The image of the tool.                                           |         |
| `--command <command>` | A command to run instead of an image, for development.           |         |
| `--timeout <seconds>` | The wall-clock limit of a pattern.                               | `10`    |
| `--parallel <n>`      | The number of the tool processes.                                | `1`     |
| `--memory <size>`     | The memory limit of a tool process.                              | `4g`    |
| `--env <KEY=VALUE>`   | An environment variable of the tool. It can be given many times. |         |
| `--limit <n>`         | The number of the patterns to run from the head of the dataset.  |         |

A tool process runs with one CPU and without the network.
Running the runner again with the same conditions continues from the patterns that the output already has.

### Environment variables of the tools

| Tool          | Variable               | Description                                                                        |
| ------------- | ---------------------- | ---------------------------------------------------------------------------------- |
| `recheck`     | `RECHECK_PARAMETERS`   | The parameters of the checks in JSON.                                              |
| `redos-detector` | `REDOS_DETECTOR_OPTIONS` | The options of `isSafePattern` in JSON.                                       |
| `redoshunter` | `REDOSHUNTER_LANGUAGE` | The language whose engine validates the attack strings: `js` (default) or `java`.  |
| `scslre`      | `SCSLRE_OPTIONS`       | The options of `analyse` in JSON.                                                  |
| JVM tools     | `JAVA_TOOL_OPTIONS`    | The options of the JVM.                                                            |

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

## Notes on the tools

- `regex-static-analysis`: the flags are not used. The degree of the analysis is the degree of ambiguity, so the result has it plus one.
- `rescue`: the flags are not used. Its genetic algorithm takes random numbers without a seed, so results differ between runs.
- `regex-matching-analyzer`: the flags `g` and `m` are removed, as its parser does not accept them. The sources of the tool are compiled together with the wrapper instead of running its sbt build.
- `redoshunter`: the jar of its Maven build has the classes of a dependency (`dk.brics.automaton`) over its own modified classes, so the image puts the classes of the tool first on the class path.
- `recheck`: a timeout by its own `timeout` parameter is also recorded as `timeout`.
- `redos-detector`: it reports a pattern as not safe when the check hits one of its limits. `reason` of the result is the limit, and a hit of its own time limit is recorded as `timeout`.
- `safe-regex`: it reports an invalid pattern as not safe, so the wrapper compiles the pattern first and records the failure as `error`.
- `scslre`: it reports no attack string. `reports` of the result has the type, the character and the positions of the quantifiers of each report.

## Adding a tool

Add `tools/<tool>/Dockerfile`.
The entry point of the image writes `{"ready": true}` on a line of the standard output, and then, for each line `{"source": ..., "flags": ...}` of the standard input, writes the result on a line.
A tool on the JVM can extend `tools/wrapper/Wrapper.scala`, and a tool on Node.js can use `tools/wrapper/wrapper.mjs`.
