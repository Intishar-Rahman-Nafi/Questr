# =============================================================================
# QUESTR Backend — Multi-stage production Dockerfile
#
# Stage 1 (build):   Compiles the Spring Boot fat JAR using Maven + JDK 21.
#                     This stage is thrown away after the build — none of its
#                     ~700MB of build tools (Maven, full JDK) end up in the
#                     final image.
# Stage 2 (runtime): Copies ONLY the compiled JAR into a slim JRE-only Alpine
#                     image. Runs as a dedicated non-root user for security.
#
# Result: ~180-220MB final image vs. ~700MB+ if we shipped the build stage.
# =============================================================================

# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy pom.xml first and download dependencies BEFORE copying source code.
# Docker caches layers — as long as pom.xml doesn't change, this dependency
# layer is reused on every rebuild, even if you change a .java file. This is
# the single biggest speedup trick in Docker builds for JVM/Node projects.
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN mvn -B dependency:go-offline -DskipTests

# Now copy source and build. Tests are skipped here intentionally — they
# should already have run in CI (see .github/workflows/deploy.yml) before an
# image is ever built. Re-running them at image-build time would slow down
# every single deploy for no benefit.
COPY src ./src
RUN mvn -B clean package -DskipTests

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# curl is needed for the Docker/Compose HEALTHCHECK below (hits /actuator/health)
RUN apk add --no-cache curl \
    && addgroup -S questr && adduser -S questr -G questr

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown questr:questr app.jar

# Never run application containers as root — limits blast radius of any
# container-escape vulnerability.
USER questr

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# -XX:MaxRAMPercentage lets the JVM heap scale with whatever memory limit the
# container is given (e.g. `deploy.resources.limits.memory: 1G` in
# docker-compose.prod.yml) instead of using a hardcoded -Xmx value that could
# either waste memory or OOM-kill the container.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

