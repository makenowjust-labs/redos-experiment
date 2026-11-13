# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-8@sha256:0f402712b7a469189cfea5ec69cdcadcb32b22aaf1ea62777ab925c3d8470175 AS build
ADD --checksum=sha256:eb41442f870bcae75aa5d6622ac96a173dae14fed59f71c97125eb21536bb060 \
    https://github.com/VirtusLab/scala-cli/releases/download/v1.18.0/scala-cli-x86_64-pc-linux.gz /scala-cli.gz
RUN gunzip -c /scala-cli.gz > /usr/local/bin/scala-cli && chmod +x /usr/local/bin/scala-cli

ARG SOURCE
ADD ${SOURCE} /source
WORKDIR /source
# The jar of the package has the classes of a dependency over the classes of the tool, so the classes are taken apart.
RUN mvn -B -q package -DskipTests dependency:copy-dependencies \
    && mkdir /tool && cp -r target/classes /tool/classes && cp -r target/dependency /tool/dependency

COPY --from=wrapper Wrapper.scala /wrapper/
COPY Main.scala /wrapper/
RUN scala-cli --power package --server=false --jvm system --scala 3.3.7 --dep com.lihaoyi::ujson:3.3.1 \
    --compile-only-jar /tool/classes $(printf -- '--compile-only-jar %s ' /tool/dependency/*.jar) /wrapper \
    --main-class redos_experiment.Main --assembly --output /wrapper.jar

FROM eclipse-temurin:8-jre@sha256:d1450af461d0d791f3b405895baf11ad37881a66e9a4dd9bbe2cd10d99278caf AS java

FROM node:26.13.0-bookworm-slim@sha256:add5c17c14752c54d2f3d519ec46a3815684d41c33f198bcb09fb998f7dcbf68
COPY --from=java /opt/java/openjdk /opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:$PATH
COPY --from=build /tool /tool
COPY --from=build /wrapper.jar /wrapper.jar
# The tool runs `js/attack.js` of the working directory to validate an attack string.
COPY --from=build /source/js /redoshunter/js
WORKDIR /redoshunter
ENTRYPOINT ["java", "-cp", "/wrapper.jar:/tool/classes:/tool/dependency/*", "redos_experiment.Main"]
