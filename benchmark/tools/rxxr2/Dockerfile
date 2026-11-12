# syntax=docker/dockerfile:1

FROM ocaml/opam:debian-12-ocaml-4.14@sha256:69009e64f81a9444976858cacab4c5363a8ef9ba9e8e6e5c278b6be96c172e87 AS build
# The site of the authors (http://www.cs.bham.ac.uk/~hxt/research/rxxr2/) does not have the archive anymore.
# This is the copy of it that the authors of vuln-regex-detector took in 2018.
ADD --checksum=sha256:b43bedb17b8d023f2f3a8cf79762e5a4946930b72d03b0b6e4aaefbdd0b06245 --chown=opam:opam \
    https://github.com/davisjam/redos-detector-backup/raw/874660e15e4af3c10a3cc47a9a5caf87fb29a21b/rxxr2.zip \
    /home/opam/rxxr2.zip
WORKDIR /home/opam
RUN unzip -q rxxr2.zip && cd rxxr/code && opam exec -- bash build.sh \
    && mkdir /home/opam/tool \
    && cp scan.bin "$(opam exec -- which ocamlrun)" "$(opam exec -- ocamlc -where)"/stublibs/dllcamlstr.so \
       "$(opam exec -- ocamlc -where)"/stublibs/dllunix.so /home/opam/tool

FROM node:26.13.0-bookworm-slim@sha256:add5c17c14752c54d2f3d519ec46a3815684d41c33f198bcb09fb998f7dcbf68
COPY --from=build /home/opam/tool /tool
ENV CAML_LD_LIBRARY_PATH=/tool
COPY --from=wrapper wrapper.mjs /wrapper/
COPY main.mjs /wrapper/
ENTRYPOINT ["node", "/wrapper/main.mjs"]
