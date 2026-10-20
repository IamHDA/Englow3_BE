# Stage 1: Build application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy configuration and source code
COPY pom.xml .
COPY src ./src

# Build application jar
RUN mvn clean package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine
ARG RELEASE_SHA=unknown
ARG AI_QUEUE_SCOPE=local
ARG AI_WORKER_ENABLED=false
ENV RELEASE_SHA=${RELEASE_SHA} \
    AI_QUEUE_SCOPE=${AI_QUEUE_SCOPE} \
    AI_WORKER_ENABLED=${AI_WORKER_ENABLED}
WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
