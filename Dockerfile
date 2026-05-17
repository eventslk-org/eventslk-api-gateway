# syntax=docker/dockerfile:1.7

# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -e dependency:go-offline

COPY src ./src

RUN mvn -B clean package -DskipTests \
    && cp target/*.jar app.jar

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
