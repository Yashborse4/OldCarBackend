# ============================================================================
# Production-Ready Multi-Stage Dockerfile for Sell The Old Car
# Java 21 Spring Boot Application with Security & Performance Optimizations
# ============================================================================

# ----------------------------------------------------------------------------
# Stage 1: Build Stage
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Install dependencies for building
RUN apk add --no-cache bash curl

# Copy gradle wrapper and configuration first (better layer caching)
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src/ src/

# Build the application (skip tests for faster build, run tests in CI)
RUN ./gradlew bootJar -x test --no-daemon && \
    mkdir -p build/dependency && \
    (cd build/dependency; jar -xf ../libs/*.jar)

# ----------------------------------------------------------------------------
# Stage 2: Production Runtime
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS production

# Security: Create non-root user
RUN addgroup --system --gid 1000 appgroup && \
    adduser --system --uid 1000 --ingroup appgroup appuser

# Install security updates and required packages
RUN apk update && \
    apk upgrade && \
    apk add --no-cache curl ca-certificates tzdata && \
    rm -rf /var/cache/apk/*

# Set timezone
ENV TZ=UTC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

# Copy application files from builder stage
COPY --from=builder /app/build/dependency/BOOT-INF/lib /app/lib
COPY --from=builder /app/build/dependency/META-INF /app/META-INF
COPY --from=builder /app/build/dependency/BOOT-INF/classes /app

# Create directories for uploads and logs with proper permissions
RUN mkdir -p /app/uploads /app/logs && \
    chown -R appuser:appgroup /app

# Switch to non-root user for security
USER appuser

# JVM Optimization for Containers
ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+ParallelRefProcEnabled \
    -XX:+AlwaysPreTouch \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=UTC \
    "

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Expose application port
EXPOSE 8080

# Volume for persistent data (uploads, logs)
VOLUME ["/app/uploads", "/app/logs"]

# Start application with optimized JVM settings
ENTRYPOINT ["sh", "-c", \
    "exec java $JAVA_OPTS \
    -cp \"/app:/app/lib/*\" \
    com.carselling.oldcar.OldCarApplication \
    --spring.config.location=optional:classpath:/,optional:file:/app/config/ \
    " \
    ]

# ----------------------------------------------------------------------------
# Stage 3: Development Runtime (Optional - with debugging enabled)
# ----------------------------------------------------------------------------
FROM production AS development

USER root

# Install debugging tools
RUN apk add --no-cache openjdk21-jdk

# Enable remote debugging
ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8 \
    "

EXPOSE 8080 5005

USER appuser

# ----------------------------------------------------------------------------
# Default to production stage
# ----------------------------------------------------------------------------
FROM production

