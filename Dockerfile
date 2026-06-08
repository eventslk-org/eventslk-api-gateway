# syntax=docker/dockerfile:1.7
###############################################################################
# EventsLK :: APIGateway (Spring Cloud Gateway) — Java 21 / Spring Boot 4.0.6
# Multi-stage, digest-pinned, non-root, healthchecked.
###############################################################################

# ---------- Stage 1: Build ----------
# maven:3.9-eclipse-temurin-21 (digest resolved 2026-06-08), pinned by digest.
FROM maven@sha256:d7e7f57407437c014571f1ad5a9955f03fc3edcb1d964067ef351fa38e798665 AS build

WORKDIR /workspace

# POM first -> dependency layer is cached independently of source changes.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests

# ---------- Stage 2: Runtime ----------
# eclipse-temurin:21-jre-jammy (digest resolved 2026-06-08), pinned by digest.
FROM eclipse-temurin@sha256:199aebeb3adcde4910695cdebfe782ada38dadb6cc8013159b58d3724451befd AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 spring \
    && useradd  --system --uid 10001 --gid spring --home-dir /app --no-create-home spring

WORKDIR /app

COPY --from=build --chown=spring:spring /workspace/target/*.jar app.jar

USER 10001:10001

# SERVER_PORT defaults to 8080 in application.yaml.
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
