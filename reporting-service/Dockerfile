# Build context is the repo root (render.yaml: dockerContext: .), because the
# service needs shared-kernel from the reactor:
#   docker build -f reporting-service/Dockerfile -t esg/reporting-service .

# --- Build stage --------------------------------------------------------------
# The repo has no Maven wrapper and eclipse-temurin:21-jdk ships no mvn, so the
# build stage uses the official Maven image on the same Temurin 21 JDK.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# The reactor needs every module's pom.xml to resolve, even the ones -pl skips;
# only shared-kernel and this service need their sources.
COPY pom.xml ./
COPY shared-kernel/ shared-kernel/
COPY reporting-service/ reporting-service/
COPY recycler-service/pom.xml recycler-service/
COPY collection-service/pom.xml collection-service/
COPY auth-service/pom.xml auth-service/
COPY e2e-tests/pom.xml e2e-tests/

# Tests are skipped: CI already ran `mvn -B verify` on this commit, and Render's
# autoDeployTrigger: checksPass never builds a commit CI hasn't passed.
RUN mvn -B -pl reporting-service -am package -DskipTests

# --- Runtime stage ------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app
# The executable jar carries the "exec" classifier (see this module's pom.xml:
# the plain reporting-service.jar is kept for failsafe's classpath).
COPY --from=build /build/reporting-service/target/reporting-service-exec.jar app.jar
USER app

# Tuned for Render's free instance (512 MB RAM, 0.1 CPU): heap sized from the
# container limit, the single-threaded collector, C1 only (faster startup on a
# fraction of a CPU), and smaller thread stacks.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"

EXPOSE 8083
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
