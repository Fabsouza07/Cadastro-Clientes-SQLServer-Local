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

# Bibliotecas necessárias para executar a interface Swing via X11.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libx11-6 \
        libxext6 \
        libxrender1 \
        libxtst6 \
        libxi6 \
        libfreetype6 \
        fontconfig \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/target/classes ./classes
COPY --from=build /workspace/target/dependency ./dependency

ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
ENV DISPLAY="host.docker.internal:0.0"

# A aplicação é Swing e precisa de um servidor X do host.
CMD ["java", "-cp", "classes:dependency/*", "br.com.cadastro.app.Main"]
