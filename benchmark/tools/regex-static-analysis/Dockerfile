# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-8@sha256:0f402712b7a469189cfea5ec69cdcadcb32b22aaf1ea62777ab925c3d8470175 AS build
ADD --checksum=sha256:eb41442f870bcae75aa5d6622ac96a173dae14fed59f71c97125eb21536bb060 \
    https://github.com/VirtusLab/scala-cli/releases/download/v1.18.0/scala-cli-x86_64-pc-linux.gz /scala-cli.gz
RUN gunzip -c /scala-cli.gz > /usr/local/bin/scala-cli && chmod +x /usr/local/bin/scala-cli

ARG SOURCE
ADD ${SOURCE} /source
WORKDIR /source
RUN mvn -B -q package -DskipTests dependency:copy-dependencies \
    && mkdir /tool && cp target/*.jar target/dependency/*.jar /tool

COPY --from=wrapper Wrapper.scala /wrapper/
COPY Main.scala /wrapper/
RUN scala-cli --power package --server=false --jvm system --scala 3.3.7 --dep com.lihaoyi::ujson:3.3.1 \
    $(printf -- '--compile-only-jar %s ' /tool/*.jar) /wrapper \
    --main-class redos_experiment.Main --assembly --output /wrapper.jar

FROM eclipse-temurin:8-jre@sha256:d1450af461d0d791f3b405895baf11ad37881a66e9a4dd9bbe2cd10d99278caf
COPY --from=build /tool /tool
COPY --from=build /wrapper.jar /wrapper.jar
ENTRYPOINT ["java", "-cp", "/wrapper.jar:/tool/*", "redos_experiment.Main"]
