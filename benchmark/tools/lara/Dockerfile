# syntax=docker/dockerfile:1

# The artifact of the paper at https://doi.org/10.5281/zenodo.21485406 has the sources of the tool in `lara`.
FROM busybox:1.37@sha256:a7dbc319b1438115a207e11930dc4bb3e643dcb3ec0bdd694bb36cd8fad289ec AS source
ADD --checksum=sha256:c83f4992c974de4e6077e8922573e61003825c8fc5e931171e25903e56caeba6 \
    https://zenodo.org/records/21485406/files/lara-data-artifacts.zip /lara.zip
RUN unzip -q /lara.zip 'lara-data-artifacts/lara/*' -x 'lara-data-artifacts/lara/target/*' -d /

FROM rust:1.96.0-bookworm@sha256:5e2214abe154fe26e39f64488952e5c991eeed1d6d6da7cc8381ae83927f0cfc AS build
COPY --from=source /lara-data-artifacts/lara /source
WORKDIR /source
RUN cargo build --release --locked

FROM node:26.13.0-bookworm-slim@sha256:add5c17c14752c54d2f3d519ec46a3815684d41c33f198bcb09fb998f7dcbf68
COPY --from=build /source/target/release/ere /usr/local/bin/ere
COPY --from=wrapper wrapper.mjs /wrapper/
COPY main.mjs /wrapper/
ENTRYPOINT ["node", "/wrapper/main.mjs"]
