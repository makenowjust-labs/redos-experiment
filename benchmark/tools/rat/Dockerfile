# syntax=docker/dockerfile:1

# The compiler has flambda, as the README of rat asks for it in a benchmark.
FROM ocaml/opam:debian-12-ocaml-4.14-flambda@sha256:9072f94041c0ed78a17ed3cd69e0a004a045f57936dc1e666202b046f27037a2 AS build
RUN opam install -y dune.3.19.1 menhir.20240715

ARG SOURCE
ADD --chown=opam:opam ${SOURCE} /home/opam/source
WORKDIR /home/opam/source
RUN opam exec -- dune build -p rat

FROM node:26.13.0-bookworm-slim@sha256:add5c17c14752c54d2f3d519ec46a3815684d41c33f198bcb09fb998f7dcbf68
COPY --from=build /home/opam/source/_build/install/default/bin/rat /usr/local/bin/rat
COPY --from=wrapper wrapper.mjs /wrapper/
COPY main.mjs /wrapper/
ENTRYPOINT ["node", "/wrapper/main.mjs"]
