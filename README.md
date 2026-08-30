# 🚀 OpenBounty — Decentralized Challenge & Bounty Collaboration Platform

[![CI Pipeline](https://github.com/gargnikunj991-ux/OpenBounty/actions/workflows/ci.yml/badge.svg)](https://github.com/gargnikunj991-ux/OpenBounty/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-brightgreen.svg)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

**OpenBounty** is an open-source platform connecting **Clients/Organizations** who have technical and real-world challenges with **Developers/Solvers** who propose, build, and deliver milestone-verified solutions.

---

## 📌 Features

- **🔐 Role-Based Access Control (RBAC)**: Secure access tailored for `ROLE_CLIENT`, `ROLE_DEVELOPER`, and `ROLE_ADMIN`.
- **🎯 Challenge & Bounty Lifecycle**: Full state-machine tracking (`OPEN` ➔ `IN_REVIEW` ➔ `ASSIGNED` ➔ `IN_PROGRESS` ➔ `COMPLETED`).
- **📑 Structured Proposals**: Developers submit bids with time estimates, breakdown milestones, and approaches.
- **🏆 Milestone Deliverable Tracking**: Proof of work submission with GitHub/live demo URLs and client approval gates.
- **🔑 Stateless Security**: JWT-based authentication with BCrypt password hashing.
- **📖 Interactive API Docs**: Built-in Swagger OpenAPI 3.0 documentation and UI.

---

## 🛠 Tech Stack

- **Language:** Java 21 (LTS)
- **Framework:** Spring Boot 3.3.3
  - Spring Web (RESTful APIs)
  - Spring Data JPA (Hibernate ORM)
  - Spring Security (Stateless JWT Auth)
  - Spring Boot Actuator (Health & Metrics)
  - Spring Boot Validation (Jakarta Validation)
- **Database:** PostgreSQL (Production) / H2 (Dev/Testing)
- **Documentation:** SpringDoc OpenAPI 2.6.0 (Swagger UI)
- **Utilities:** Lombok, Dotenv Java (environment config)
- **Build Tool:** Maven 3.9+

---

## 🏗 Architecture & Design

For deep architectural decisions, ER diagrams, and state transitions, see:
- [📖 System Design Document](SYSTEM_DESIGN.md)
- [🗺 Product Roadmap](ROADMAP.md)

```text
[ Client (Web/Mobile/Postman) ]
              │ HTTP Requests
              ▼
[ JwtAuthenticationFilter & SecurityFilterChain ]
              │ Authenticated & Authorized Requests
              ▼
[ Controller Layer (@RestController) ]
              │
              ▼
[ Service Layer (@Service) ]
              │
              ▼
[ Repository Layer (@Repository) ]
              │
              ▼
[ PostgreSQL Database ]
```

---

## ⚡ Quick Start

### 1. Prerequisites
- **JDK 21** installed and configured in your `PATH`
- **Maven 3.9+**
- **PostgreSQL 15+** running locally or via Docker

### 2. Clone the Repository
```bash
git clone https://github.com/gargnikunj991-ux/OpenBounty.git
cd OpenBounty
```

### 3. Configure Environment Variables
Copy `.env.example` to create your local `.env` file:
```bash
# Windows PowerShell
Copy-Item .env.example .env

# Linux / macOS
cp .env.example .env
```

Update your `.env` with your PostgreSQL database credentials and a 256-bit JWT secret:
```properties
SERVER_PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/openbounty_db
DB_USERNAME=postgres
DB_PASSWORD=postgres
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
JWT_EXPIRATION_MS=86400000
```

### 4. Build & Run
```bash
# Build with Maven
mvn clean compile

# Run the Spring Boot application
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

---

## 📚 API Documentation

Once the application is running, explore and test the interactive API documentation:
- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON Spec:** [http://localhost:8080/api-docs](http://localhost:8080/api-docs)
- **Actuator Health Endpoint:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## 🧪 Testing

Run all unit and integration tests:
```bash
mvn test
```

---

## 📄 License

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details.
