# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584 AS build
ADD --checksum=sha256:eb41442f870bcae75aa5d6622ac96a173dae14fed59f71c97125eb21536bb060 \
    https://github.com/VirtusLab/scala-cli/releases/download/v1.18.0/scala-cli-x86_64-pc-linux.gz /scala-cli.gz
RUN gunzip -c /scala-cli.gz > /usr/local/bin/scala-cli && chmod +x /usr/local/bin/scala-cli

ARG SOURCE
ADD ${SOURCE} /source
WORKDIR /source
# The validator takes the type name `SLQ` to validate an attack string by a search, but `SLQPattern` is named `POLS`,
# so it validated every attack string by a match of the whole input.
RUN sed -i 's/type.contains("SLQ")/type.contains("POLS")/' src/main/java/rengar/dynamic/validator/Validator.java \
    && grep -q 'type.contains("POLS")' src/main/java/rengar/dynamic/validator/Validator.java
RUN mvn -B -q package -DskipTests dependency:copy-dependencies \
    && mkdir /tool && cp -r target/classes /tool/classes && cp -r target/dependency /tool/dependency

COPY --from=wrapper Wrapper.scala /wrapper/
COPY Main.scala /wrapper/
RUN scala-cli --power package --server=false --jvm system --scala 3.3.7 --dep com.lihaoyi::ujson:3.3.1 \
    --compile-only-jar /tool/classes $(printf -- '--compile-only-jar %s ' /tool/dependency/*.jar) /wrapper \
    --main-class redos_experiment.Main --assembly --output /wrapper.jar

FROM eclipse-temurin:17-jre@sha256:0b6521287f850ec1c4b31c9074c5a1ae1d67c5922c3fefd21065c7c32e244d4a
COPY --from=build /tool /tool
COPY --from=build /wrapper.jar /wrapper.jar
# The classes of the tool are compiled with the preview features of Java 17, and its regex engine uses `sun.text`.
ENTRYPOINT ["java", "--enable-preview", "--add-exports", "java.base/sun.text=ALL-UNNAMED", \
    "-cp", "/wrapper.jar:/tool/classes:/tool/dependency/*", "redos_experiment.Main"]
