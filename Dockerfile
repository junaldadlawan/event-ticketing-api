# Multi-stage build: compile with Maven + JDK 21, run on a slim JRE 21.
# Matches pom.xml's <java.version>21</java.version> / Spring Boot 4.1.1.

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Cache dependencies separately from source so a source-only change doesn't
# re-download the whole Maven repo.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

COPY src src
RUN ./mvnw -q clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS run
WORKDIR /app

# curl for the HEALTHCHECK below - the base JRE image doesn't include it.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Non-root - the base image ships a "ubuntu" user (uid 1000); a dedicated
# app user keeps this independent of that image detail.
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /build/target/*.jar app.jar
RUN chown app:app app.jar
USER app

# server.port in application.properties.
EXPOSE 8081

# Container-level healthcheck - ECS's own task/ALB health check (see
# deploy/aws/terraform/alb.tf) is what actually gates traffic; this is a
# secondary signal `docker run`/local compose can also see. No Actuator
# dependency in pom.xml, so this hits a real endpoint rather than
# /actuator/health.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS "http://localhost:8081/api/v1/events?size=1" > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
