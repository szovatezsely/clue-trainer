# ---------- build ----------
# Pinned to the Ubuntu 22.04 base. The rolling `21-jdk`/`21-jre` tags moved to
# a 24.04 base whose JRE build segfaults on start under the WSL2 kernel Docker
# Desktop ships; the jammy builds of the same 21.0.12 release are fine.
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

# Resolve the Gradle distribution and the dependency graph first: these layers
# are reused on every rebuild that only touches source files.
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath -q

COPY src src
RUN ./gradlew --no-daemon installDist -q

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-jammy

# curl is only here so the container can report its own health to compose.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/build/install/geoclue-trainer ./

ENV PORT=8080 \
    DATA_DIR=/data \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

RUN useradd --system --uid 10001 --create-home trainer \
    && mkdir -p /data \
    && chown -R trainer:trainer /data /app
USER trainer

VOLUME /data
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=8 \
    CMD curl -fsS "http://127.0.0.1:${PORT}/api/health" > /dev/null || exit 1

ENTRYPOINT ["/app/bin/geoclue-trainer"]
