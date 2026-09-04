# ==============================================================================
# OpenBounty — Multi-Stage Production Dockerfile (Java 21 + Spring Boot 3.3)
# ==============================================================================

# Stage 1: Build application using Maven & Eclipse Temurin JDK 21
FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Pre-cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compile source and package executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Lightweight Production JRE 21 Container
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Run as non-root system user for container security hardening
RUN addgroup -S openbounty && adduser -S openbounty -G openbounty
USER openbounty

# Copy compiled JAR artifact
COPY --from=builder /build/target/open-bounty-*.jar app.jar

EXPOSE 8080

# Production-tuned JVM memory management & G1GC flags
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
