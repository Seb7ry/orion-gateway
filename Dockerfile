# ===========================================
# ORION GATEWAY SERVICE - Dockerfile
# Multi-stage build for optimized production image
# ===========================================

# Stage 1: Build stage
FROM eclipse-temurin:21-jdk AS build

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy Maven files for dependency caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies (cached layer if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre AS runtime

# Install curl for health checks
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r orion && useradd -r -g orion orion

# Set working directory
WORKDIR /app

# Copy built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R orion:orion /app
USER orion

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Environment variables with defaults
ENV SPRING_PROFILES_ACTIVE=docker
ENV SERVER_PORT=8080
ENV GATEWAY_NAME=orion-gateway

# Gateway microservice URLs (Docker network)
ENV ORION_AUTH_URL=lb://orion-auth
ENV ORION_USER_URL=lb://orion-user
ENV ORION_PROGRAM_URL=lb://orion-program
ENV ORION_DOCUMENT_URL=lb://orion-document
ENV ORION_DRIVE_URL=lb://orion-drive

# Logging
ENV LOGGING_LEVEL=INFO
ENV GATEWAY_LOGGING_LEVEL=INFO

# Run application
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]