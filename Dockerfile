# Build da aplicação Java com Maven e JDK 25.
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory=target/dependency

# Imagem menor para executar a aplicação.
FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/target/classes ./classes
COPY --from=build /workspace/target/dependency ./dependency

ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"

# A aplicação é Swing e precisa de um servidor X do host.
CMD ["java", "-cp", "classes:dependency/*", "br.com.cadastro.app.Main"]

