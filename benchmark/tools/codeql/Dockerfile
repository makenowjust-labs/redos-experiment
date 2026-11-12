# syntax=docker/dockerfile:1

# The bundle has the CodeQL CLI 2.27.4 with the extractor and the query packs of JavaScript.
FROM node:26.13.0-bookworm-slim@sha256:add5c17c14752c54d2f3d519ec46a3815684d41c33f198bcb09fb998f7dcbf68 AS bundle
ADD --checksum=sha256:082e798c97831283a2a5f69e9198c51270e5767013d5333daeeab6520af9ce72 \
    https://github.com/github/codeql-action/releases/download/codeql-bundle-v2.27.4/codeql-bundle-javascript-linux64.tar.zst /codeql.tar.zst
# The base image has no `zstd`, so Node.js decompresses the archive.
RUN node -e 'fs.createReadStream("/codeql.tar.zst").pipe(zlib.createZstdDecompress()).pipe(process.stdout)' | tar -x -C /opt

FROM node:26.13.0-bookworm-slim@sha256:add5c17c14752c54d2f3d519ec46a3815684d41c33f198bcb09fb998f7dcbf68
COPY --from=bundle /opt/codeql /opt/codeql
ENV PATH=/opt/codeql:$PATH
COPY --from=wrapper wrapper.mjs /wrapper/
COPY qlpack.yml redos.ql /wrapper/
# A run takes the compiled query from the cache of this compilation.
RUN codeql query compile --threads=0 /wrapper/redos.ql
COPY main.mjs /wrapper/
ENTRYPOINT ["node", "/wrapper/main.mjs"]
