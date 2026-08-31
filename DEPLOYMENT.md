# OpenBounty — Production Deployment & Operations Guide

This guide covers containerization, orchestration, environment configuration, observability, and operational procedures for deploying **OpenBounty** to production.

---

## 1. Container Architecture (Multi-Stage Dockerfile)

A multi-stage build separates the Maven compilation environment from the minimal runtime JRE container to produce lightweight, secure images (~180MB).

```dockerfile
# ==============================================================================
# Stage 1: Build & Package
# ==============================================================================
FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Cache dependency layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compile and package executable jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Minimal Production JRE Runtime
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create non-root system user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy built artifact from builder
COPY --from=builder /build/target/open-bounty-*.jar app.jar

# Expose HTTP port
EXPOSE 8080

# Production JVM flags for memory & garbage collection
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 2. Local & Production Docker Compose (`docker-compose.yml`)

```yaml
version: '3.8'

services:
  # ============================================================================
  # PostgreSQL Database Service
  # ============================================================================
  postgres:
    image: postgres:16-alpine
    container_name: openbounty-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-openbounty_db}
      POSTGRES_USER: ${POSTGRES_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-postgres}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d openbounty_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ============================================================================
  # Spring Boot Backend API Service
  # ============================================================================
  api:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: openbounty-api
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SERVER_PORT: 8080
      DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-openbounty_db}
      DB_USERNAME: ${POSTGRES_USER:-postgres}
      DB_PASSWORD: ${POSTGRES_PASSWORD:-postgres}
      JWT_SECRET: ${JWT_SECRET:-404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}
      JWT_EXPIRATION_MS: ${JWT_EXPIRATION_MS:-86400000}
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health | grep UP || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 3

volumes:
  postgres_data:
    driver: local
```

---

## 3. Environment Variables Reference

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8080` | HTTP port the application binds to. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/openbounty_db` | PostgreSQL JDBC connection URL. |
| `DB_USERNAME` | `postgres` | Database username. |
| `DB_PASSWORD` | `postgres` | Database password (Must use strong secret in production). |
| `JWT_SECRET` | *Must be overridden* | 256-bit hexadecimal or Base64 secret key for HMAC-SHA256 signing. |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | JWT access token validity in milliseconds. |

---

## 4. Observability & Monitoring

### Spring Boot Actuator Endpoints
* **Liveness & Readiness Health:** `GET http://localhost:8080/actuator/health`
* **Application Info:** `GET http://localhost:8080/actuator/info`
* **JVM & HikariCP Metrics:** `GET http://localhost:8080/actuator/metrics`

### Production Connection Pool Tuning (HikariCP)
In `application.yml`:
* `maximum-pool-size: 10`
* `minimum-idle: 5`
* `connection-timeout: 30000`
* `leak-detection-threshold: 60000`

---

## 5. Deployment Commands Cheat Sheet

```bash
# 1. Start all services in detached mode
docker compose up -d

# 2. View real-time logs
docker compose logs -f api

# 3. Check health status
docker compose ps

# 4. Stop all services
docker compose down
```
