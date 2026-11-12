# syntax=docker/dockerfile:1

FROM eclipse-temurin:8-jdk@sha256:c424f46b825d495a28e8f26ca5f411585119e5d9bd9152521180b9d5e5a303c4 AS build
ADD --checksum=sha256:eb41442f870bcae75aa5d6622ac96a173dae14fed59f71c97125eb21536bb060 \
    https://github.com/VirtusLab/scala-cli/releases/download/v1.18.0/scala-cli-x86_64-pc-linux.gz /scala-cli.gz
RUN gunzip -c /scala-cli.gz > /usr/local/bin/scala-cli && chmod +x /usr/local/bin/scala-cli

ARG SOURCE
ADD ${SOURCE} /source

COPY --from=wrapper Wrapper.scala /wrapper/
COPY Main.scala /wrapper/
# The sources of the tool are compiled with the wrapper, with the Scala version and the dependency of its `build.sbt`.
RUN scala-cli --power package --server=false --jvm system --scala 2.12.11 --dep com.lihaoyi::ujson:3.3.1 \
    --dep org.scala-lang.modules::scala-parser-combinators:1.1.1 /source/src /wrapper \
    --main-class redos_experiment.Main --assembly --output /wrapper.jar

FROM eclipse-temurin:8-jre@sha256:d1450af461d0d791f3b405895baf11ad37881a66e9a4dd9bbe2cd10d99278caf
COPY --from=build /wrapper.jar /wrapper.jar
ENTRYPOINT ["java", "-jar", "/wrapper.jar"]
