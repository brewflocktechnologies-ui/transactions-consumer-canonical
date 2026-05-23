# =====================================================================
# Multi-stage Dockerfile — JDK 25 build, JRE 25 runtime.
# Final image is small (~250 MB) and runs as a non-root user.
# =====================================================================

# ── Stage 1: build ────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Cache Maven deps first
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY pom.xml ./
RUN ./mvnw -q -B dependency:go-offline

COPY src ./src
RUN ./mvnw -q -B -DskipTests package \
 && mkdir -p target/extracted \
 && java -Djarmode=tools -jar target/*.jar extract --layers --destination target/extracted

# ── Stage 2: runtime ──────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

# Container hygiene
RUN groupadd --system --gid 1001 app \
 && useradd  --system --uid 1001 --gid app --home /opt/app --shell /sbin/nologin app \
 && mkdir -p /opt/app && chown -R app:app /opt/app

WORKDIR /opt/app
USER app:app

# Layered jar — dependencies/snapshot-dependencies rarely change, app changes every build
COPY --from=build --chown=app:app /workspace/target/extracted/dependencies/        ./
COPY --from=build --chown=app:app /workspace/target/extracted/spring-boot-loader/  ./
COPY --from=build --chown=app:app /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/application/         ./

EXPOSE 8080

# Production-sane JVM defaults. Override with JAVA_TOOL_OPTIONS at runtime if needed.
ENV JAVA_TOOL_OPTIONS="\
 -XX:+UseContainerSupport \
 -XX:MaxRAMPercentage=75.0 \
 -XX:InitialRAMPercentage=50.0 \
 -XX:+ExitOnOutOfMemoryError \
 -XX:+HeapDumpOnOutOfMemoryError \
 -XX:HeapDumpPath=/tmp \
 -Djava.security.egd=file:/dev/./urandom \
 -Dfile.encoding=UTF-8 \
 -Duser.timezone=UTC"

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
