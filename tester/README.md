# tester

> ReDoS detection library benchmarkers.

## Supported Libraries

- [MakeNowJust-Labo/redos](https://github.com/MakeNowJust-Labo/redos)
- [minamide-group/regex-matching-analyzer](https://github.com/minamide-group/regex-matching-analyzer)
- [2bdenny/ReScue](https://github.com/2bdenny/ReScue)

## Usage

regex-matching-analyzer and ReScue are not published to Maven Central.
Before running them, it needs to install them to the local repository.
In addition, it is needed to apply a patch to ReScue for timeout support.

```console
$ git submodule update --init
$ pushd ../deps

$ pushd rescue
$ git apply < ../rescue.patch
$ mvn install
$ popd

$ pushd regex-matching-analyzer
$ sbt publishLocal
$ popd

$ popd
```

Then, we can run them.

```console
$ sbt 'redos/run ../../../data/regexp.json ../../../result/redos-hybrid.json hybrid'
$ sbt 'regex-matching-analyzer/run ../../../data/regexp.json ../../../result/regex-matching-analyzer.json'
$ sbt 'rescue/run ../../../data/regexp.json ../../../result/rescue.json'
```
