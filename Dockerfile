# Stage 1: Build application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy configuration and source code
COPY pom.xml .
COPY src ./src

# Build application jar
RUN mvn clean package -DskipTests
# Resolve nested libraries at build time instead of repeating archive work on every wake-up.
RUN java -Djarmode=tools -jar target/*.jar extract --destination /app/extracted --application-filename app.jar

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine
ARG RELEASE_SHA=unknown
ARG AI_QUEUE_SCOPE=local
ARG AI_WORKER_ENABLED=false
ARG JVM_STARTUP_OPTIONS=""
ENV RELEASE_SHA=${RELEASE_SHA} \
    AI_QUEUE_SCOPE=${AI_QUEUE_SCOPE} \
    AI_WORKER_ENABLED=${AI_WORKER_ENABLED} \
    JAVA_TOOL_OPTIONS=${JVM_STARTUP_OPTIONS}
WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built jar from builder stage
COPY --from=builder --chown=spring:spring /app/extracted/ ./

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
