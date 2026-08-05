# syntax=docker/dockerfile:1

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Layer-cache dependencies first
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# curl for the compose healthcheck
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
