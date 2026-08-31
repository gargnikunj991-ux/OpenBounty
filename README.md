# 🚀 OpenBounty — Decentralized Challenge & Bounty Collaboration Platform

[![CI Pipeline](https://github.com/gargnikunj991-ux/OpenBounty/actions/workflows/ci.yml/badge.svg)](https://github.com/gargnikunj991-ux/OpenBounty/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-brightgreen.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

**OpenBounty** is an enterprise-ready, open-source platform connecting **Clients/Organizations** who have technical challenges with **Developers/Solvers** who propose, build, and deliver milestone-verified solutions.

---

## 📚 Complete Technical Documentation Suite

For deep architectural insights, database designs, API specifications, and operational guides:

| Document | Description |
| :--- | :--- |
| [🚀 Zero-to-Launch Guide (`DEVELOPMENT_TO_LAUNCH_GUIDE.md`)](DEVELOPMENT_TO_LAUNCH_GUIDE.md) | Master end-to-end playbook from initial setup to production deployment and user acquisition. |
| [📖 System Design (`SYSTEM_DESIGN.md`)](SYSTEM_DESIGN.md) | High-level system architecture, ER diagrams, and state machine lifecycle. |
| [🗺 Development Roadmap (`ROADMAP.md`)](ROADMAP.md) | 10-phase engineering roadmap from scratch to production deployment. |
| [🔌 API Specification (`API_SPECIFICATION.md`)](API_SPECIFICATION.md) | Exhaustive REST contracts, JSON schemas, headers, query params & RFC 7807 error models. |
| [🗄 Database Schema (`DATABASE_SCHEMA.md`)](DATABASE_SCHEMA.md) | PostgreSQL relational schema, DDL, constraints, indexing strategies, and data dictionary. |
| [🔐 Security Architecture (`SECURITY_MODEL.md`)](SECURITY_MODEL.md) | Stateless JWT authentication, RBAC permission matrix, password hashing & OWASP mitigations. |
| [🏛 Architecture Decisions (`ARCHITECTURE_DECISIONS.md`)](ARCHITECTURE_DECISIONS.md) | Architecture Decision Records (ADRs) capturing key engineering trade-offs. |
| [🐳 Deployment & Operations (`DEPLOYMENT.md`)](DEPLOYMENT.md) | Multi-stage Docker containerization, `docker-compose.yml`, JVM tuning, and monitoring. |
| [🤝 Contributing Guide (`CONTRIBUTING.md`)](CONTRIBUTING.md) | Contribution standards, Git workflow, branch naming, and PR checklist. |
| [🤖 Agent & AI Guidelines (`AGENT.md`)](AGENT.md) | Senior engineering co-pilot and pair-programming architecture principles. |

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
- **Database:** PostgreSQL 16 (Production) / H2 (Dev/Testing)
- **Documentation:** SpringDoc OpenAPI 2.6.0 (Swagger UI)
- **Utilities:** Lombok, Dotenv Java
- **Containerization:** Docker & Docker Compose
- **Build Tool:** Maven 3.9+

---

## 🏗 High-Level Architecture

```text
[ Client (Web / Mobile / CLI) ]
               │ HTTP Requests
               ▼
[ JwtAuthenticationFilter & SecurityFilterChain ]
               │ Authenticated & Authorized Requests
               ▼
[ Controller Layer (@RestController) ] ── (Jakarta @Valid DTOs, RFC 7807 problem details)
               │
               ▼
[ Service Layer (@Service) ] ─────────── (Business domain logic, state transitions, @Transactional)
               │
               ▼
[ Repository Layer (@Repository) ] ───── (Spring Data JPA, JPQL, indexing, pagination)
               │
               ▼
[ PostgreSQL Database ] ──────────────── (Relational tables, foreign keys, check constraints)
```

---

## ⚡ Quick Start

### 1. Prerequisites
- **JDK 21** installed and configured in your `PATH`
- **Maven 3.9+**
- **Docker & Docker Compose** (for PostgreSQL database)

### 2. Clone the Repository
```bash
git clone https://github.com/gargnikunj991-ux/OpenBounty.git
cd OpenBounty
```

### 3. Start Database & Run Backend
```bash
# 1. Start PostgreSQL container
docker compose up -d postgres

# 2. Copy environment variables template
cp .env.example .env

# 3. Build & Run Spring Boot application
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

---

## 📚 Interactive API Documentation

Once the application is running:
- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON Spec:** [http://localhost:8080/api-docs](http://localhost:8080/api-docs)
- **Actuator Health Endpoint:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## 🧪 Testing

Run all unit and integration test suites:
```bash
mvn clean test
```

---

## 📄 License

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details.
