# syntax=docker/dockerfile:1

# `JAR` is `release` to take the jar of the release `VERSION`,
# or `local` to take `recheck.jar` of the build context `jar`.
ARG JAR=release

FROM scratch AS release
ARG VERSION
ADD https://github.com/makenowjust-labs/recheck/releases/download/v${VERSION}/recheck.jar /recheck.jar

FROM scratch AS local
COPY --from=jar recheck.jar /recheck.jar

FROM ${JAR} AS selected

FROM eclipse-temurin:27-jre@sha256:05a57c9f4d6d8bf8d8c2213049ea816609672ffcc14463a9c83433a610703e10 AS java

FROM node:26.13.0-bookworm-slim@sha256:add5c17c14752c54d2f3d519ec46a3815684d41c33f198bcb09fb998f7dcbf68
COPY --from=java /opt/java/openjdk /opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:$PATH
COPY --from=selected /recheck.jar /recheck.jar
COPY --from=wrapper wrapper.mjs /wrapper/
COPY main.mjs /wrapper/
ENTRYPOINT ["node", "/wrapper/main.mjs"]
