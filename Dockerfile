# ────────────────────────────────────────────────────────────────────────────
# Multi-stage Dockerfile for the TMAPI Spring Boot application
# Stage 1: Build (Gradle)
# Stage 2: Runtime (minimal JRE 21 + non-root user)
# ────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and build files first (leverages Docker layer cache)
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .

# Download dependencies in a separate layer (cached unless build.gradle changes)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet

# Copy source and build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: non-root user
RUN addgroup -S tmapi && adduser -S tmapi -G tmapi
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Change ownership to the non-root user
RUN chown tmapi:tmapi app.jar
USER tmapi

# JVM flags tuned for containerized environments:
#   -XX:+UseContainerSupport      — respect cgroup CPU/memory limits
#   -XX:MaxRAMPercentage=75.0     — use up to 75% of container memory for heap
#   -XX:+UseZGC                   — low-latency GC (Java 21)
#   -Djava.security.egd=...       — faster SecureRandom for JWT on Linux
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseZGC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.profiles.active=prod"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
