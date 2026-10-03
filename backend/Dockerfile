# ==============================================================================
# Smart Expense Tracker — backend (Spring Boot)
# Multi-stage build: Maven compiles + packages the jar, then a slim JRE image
# runs it. Keeps the final image small (no JDK, no Maven cache).
# ==============================================================================

# ---- Stage 1: build ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy the POM first so dependency downloads are cached in their own layer.
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Copy sources and package (tests skipped here; run them in CI instead).
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Stage 2: runtime --------------------------------------------------------
FROM eclipse-temurin:17-jre

WORKDIR /app

# curl is needed for the compose healthcheck below (the JRE image has none).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Only the built jar comes across from the build stage.
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Run the Spring Boot application.
ENTRYPOINT ["java", "-jar", "app.jar"]
