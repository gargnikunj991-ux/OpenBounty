# ☕ OpenBounty — Backend Service (Spring Boot 3.3 + Java 21)

This folder contains the core backend REST API service for OpenBounty, built with **Spring Boot 3.3**, **Java 21**, **Spring Data JPA**, and **PostgreSQL**.

---

## 🛠 Tech Stack

- **Java**: 21 LTS (Eclipse Temurin)
- **Framework**: Spring Boot 3.3.x
- **ORM & Persistence**: Spring Data JPA / Hibernate 6
- **Database**: PostgreSQL 16
- **Security**: Spring Security 6 + Stateless JWT RBAC
- **Documentation**: OpenAPI 3 / SpringDoc Swagger UI
- **Build Tool**: Apache Maven 3.9+
- **Containerization**: Docker multi-stage build

---

## 🚀 Getting Started

### 1. Prerequisites
- JDK 21 installed (`java -version`)
- Maven 3.9+ installed (`mvn -version`)
- Running PostgreSQL instance (or launch via root `docker-compose up -d postgres`)

### 2. Environment Configuration
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Ensure database credentials match your local PostgreSQL setup:
```properties
DB_URL=jdbc:postgresql://localhost:5432/openbounty_db
DB_USERNAME=postgres
DB_PASSWORD=postgres
SERVER_PORT=8080
JWT_SECRET=your_base64_or_hex_encoded_secret_key
JWT_EXPIRATION_MS=86400000
```

### 3. Build & Run
From inside the `backend` directory:
```bash
# Compile and package
mvn clean package -DskipTests

# Run unit and integration tests
mvn clean verify

# Start Spring Boot locally
mvn spring-boot:run
```

The API will be live at: `http://localhost:8080`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **API Specs**: `http://localhost:8080/v3/api-docs`
- **Actuator Health**: `http://localhost:8080/actuator/health`

---

## 📁 Package Structure

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/com/openbounty/
│   │   │   ├── OpenBountyApplication.java
│   │   │   ├── config/             # OpenAPI, Security, JPA configs
│   │   │   ├── controller/         # REST API Controllers
│   │   │   ├── dto/                # Request & Response DTOs
│   │   │   ├── enums/              # Domain Enums (Roles, Statuses)
│   │   │   ├── exception/          # Global Exception Handler & Errors
│   │   │   ├── model/              # JPA Entities (User, Bounty, Proposal, etc.)
│   │   │   ├── repository/         # Spring Data JPA Repositories
│   │   │   └── service/            # Core Business Logic
│   │   └── resources/
│   │       └── application.yml     # Application configuration
├── Dockerfile                      # Multi-stage production container build
├── pom.xml                         # Maven dependencies & build plugins
└── README.md
```
