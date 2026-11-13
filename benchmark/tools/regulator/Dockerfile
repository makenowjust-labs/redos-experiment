# syntax=docker/dockerfile:1

# The README of the fuzzer says that its build is tested on Ubuntu 20.04.
# `python-is-python3` is not in its list of the packages, but the build of Node.js runs `python`.
FROM ubuntu:20.04@sha256:8feb4d8ca5354def3d8fce243717141ce31e2c428701f6682bd2fafe15388214 AS build
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y \
        git build-essential python3 python3-distutils libicu-dev python-is-python3 \
    && rm -rf /var/lib/apt/lists/*

ARG SOURCE
ADD ${SOURCE} /source
WORKDIR /source/fuzzer
# The Makefile fetches the commit `NODE_COMMIT` of Node.js from GitHub and builds it.
# Node.js is built by its own target first, because the rule of `make node` that copies its libraries has many targets,
# and `-j` runs it for them at once.
# The files of the build are removed in the same layer, because a build cache does not keep a layer of their size.
RUN make -j16 build/node/out/Debug/node && make node && make -j16 \
    && cp build/fuzzer_stripped /fuzzer && rm -r build deps/from_node
# The driver of the commit fails with an exception for most patterns without the patch.
COPY driver.patch /
RUN patch -p1 -d /source < /driver.patch

FROM node:26.13.0-bookworm-slim@sha256:add5c17c14752c54d2f3d519ec46a3815684d41c33f198bcb09fb998f7dcbf68 AS node

# Python is 3.8 as on Ubuntu 20.04.
# Node.js runs the wrapper, and also `time_exec.js` of the driver, which measures the time of a match.
FROM python:3.8.20-slim-bookworm@sha256:1d52838af602b4b5a831beb13a0e4d073280665ea7be7f69ce2382f29c5a613f
COPY requirements.txt /wrapper/
RUN pip install --no-cache-dir --no-deps --no-build-isolation --require-hashes -r /wrapper/requirements.txt
COPY --from=node /usr/local/bin/node /usr/local/bin/
COPY --from=node /usr/lib/x86_64-linux-gnu/libatomic.so.1* /usr/lib/x86_64-linux-gnu/
COPY --from=build /fuzzer /regulator/fuzzer
COPY --from=build /source/driver /regulator/driver
COPY --from=wrapper wrapper.mjs /wrapper/
COPY main.mjs run.py /wrapper/
ENV REGULATOR_FUZZER=/regulator/fuzzer PYTHONPATH=/regulator/driver
ENTRYPOINT ["node", "/wrapper/main.mjs"]
