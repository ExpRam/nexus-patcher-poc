ARG BASE_IMAGE=sonatype/nexus3:latest
ARG JAVA_VERSION=21

FROM eclipse-temurin:${JAVA_VERSION}-jdk-noble AS build

WORKDIR /workspace

COPY gradlew ./
COPY gradle/ ./gradle/
COPY settings.gradle.kts build.gradle.kts ./

RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon help

COPY src/ ./src/

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --build-cache shadowJar

FROM ${BASE_IMAGE} AS final

COPY --from=build \
    --chmod=0444 \
    /workspace/build/libs/*.jar \
    /opt/javaagent/agent.jar

ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/javaagent/agent.jar"