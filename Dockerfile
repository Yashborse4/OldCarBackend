# Multi-stage build for Spring Boot application with security hardening
FROM openjdk:17-jdk-slim as build

# Set working directory
WORKDIR /app

# Install build dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Copy Gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Download dependencies (this layer will be cached if build.gradle doesn't change)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src ./src

# Build the application
RUN ./gradlew build --no-daemon -x test

# Production stage with security hardening
FROM openjdk:17-jre-slim

# Install security updates and required packages
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y --no-install-recommends \
    curl \
    ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Create non-root user with specific UID/GID
RUN groupadd -r appuser -g 1001 && \
    useradd -r -g appuser -u 1001 -d /app -s /sbin/nologin appuser

# Set working directory
WORKDIR /app

# Create necessary directories with proper permissions
RUN mkdir -p /app/uploads /app/logs /app/tmp && \
    chown -R appuser:appuser /app && \
    chmod -R 755 /app

# Copy the built JAR from build stage
COPY --from=build --chown=appuser:appuser /app/build/libs/*.jar app.jar

# Set JVM security options
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:+UseG1GC -XX:+UseStringDeduplication"
ENV SPRING_PROFILES_ACTIVE=prod

# Use non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check with security considerations
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f --max-time 5 http://localhost:8080/actuator/health || exit 1

# Security labels
LABEL maintainer="security@company.com" \
      description="Secure Car Selling Application" \
      version="1.0.0"

# Run the application with security-focused JVM options
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Djava.awt.headless=true", "-jar", "app.jar"]
