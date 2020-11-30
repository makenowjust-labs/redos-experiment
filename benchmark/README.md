# tester

> ReDoS detection library benchmarkers.

## Supported Libraries

- [MakeNowJust-Labo/redos](https://github.com/MakeNowJust-Labo/redos)
- [minamide-group/regex-matching-analyzer](https://github.com/minamide-group/regex-matching-analyzer)
- [NicolaasWeideman/RegexStaticAnalysis](https://github.com/NicolaasWeideman/RegexStaticAnalysis)
- [2bdenny/ReScue](https://github.com/2bdenny/ReScue)

## Usage

regex-matching-analyzer, RegexStaticAnalysis and ReScue are not published to Maven Central.
Before running them, it needs to install them to the local repository.
In addition, it is needed to apply a patch to ReScue for timeout support.

```console
$ git submodule update --init
$ pushd ../dep

$ pushd regex-matching-analyzer
$ sbt publishLocal
$ popd

$ pushd RegexStaticAnalysis
$ mvn install
$ popd

$ pushd ReScue
$ git apply < ../ReScue.patch
$ mvn install
$ popd

$ popd
```

Then, we can run them.

```console
$ sbt 'redos/run ../../../data/redos-regexp.json ../../../result/redos-hybrid.json hybrid'
$ sbt 'regex-matching-analyzer/run ../../../data/redos-regexp.json ../../../result/regex-matching-analyzer.json'
$ sbt 'regex-static-analysis/run ../../../data/redos-regexp.json ../../../result/regex-static-analysis.json'
$ sbt 'rescue/run ../../../data/redos-regexp.json ../../../result/rescue.json'
```
