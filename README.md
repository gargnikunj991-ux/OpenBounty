# 🚀 OpenBounty — Dual-Track Challenge & Bounty Marketplace Platform

[![CI Pipeline](https://github.com/gargnikunj991-ux/OpenBounty/actions/workflows/ci.yml/badge.svg)](https://github.com/gargnikunj991-ux/OpenBounty/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-brightgreen.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)
![Docker](https://img.shields.io/badge/Docker-Enabled-blue.svg)

**OpenBounty** is an enterprise-grade, escrow-backed challenge and bounty collaboration marketplace connecting **Clients/Organizations** who have technical challenges with **Developers/Solvers** who propose, build, and deliver milestone-verified solutions.

Architected with a **Dual-Track Strategy**: designed from day one as a **commercially viable marketplace** (automated Stripe Connect escrow, 10–15% platform take-rate, dispute protocol) while serving as a **flagship software engineering showcase** (double-entry financial ledger, pessimistic concurrency control, idempotency key guarantees, and Testcontainers testing).

---

## 🎯 The Dual-Track Architecture & Strategy

```text
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       OPENBOUNTY DUAL-TRACK STRATEGY                                   │
├────────────────────────────────────────────────────┬───────────────────────────────────────────────────┤
│          📄 TRACK 1: RESUME & TECHNICAL RIGOR      │       💰 TRACK 2: COMMERCIAL MARKETPLACE VIABILITY│
├────────────────────────────────────────────────────┼───────────────────────────────────────────────────┤
│ • Double-Entry Financial Ledger (GAAP Invariants)  │ • Stripe Connect Escrow & Automated Payout Rails  │
│ • Pessimistic Concurrency (`SELECT ... FOR UPDATE`)│ • Automated 10–15% Platform Take-Rate Commission  │
│ • Idempotent Execution (`Idempotency-Key` Headers) │ • 14-Day Inactivity Auto-Release Escrow Guard     │
│ • Real Integration Tests via Testcontainers        │ • GitHub OAuth & PR Verification Workflows        │
│ • RFC 7807 Global Problem Details Exception Model  │ • Dispute Resolution & Binding Arbitrator Portal  │
│ • Production Observability (Prometheus & Actuator) │ • Algorithmic Reputation & Social Proof Engine    │
└────────────────────────────────────────────────────┴───────────────────────────────────────────────────┘
```

### Track 1: Software Engineering Rigor (Resume / Technical Depth)
* **Double-Entry Financial Bookkeeping**: Immutable ledger entries (`DEBIT` / `CREDIT`) ensuring mathematical impossibility of money duplication, negative balances, or lost audit trails.
* **Pessimistic & Optimistic Concurrency Control**: Prevents race conditions during simultaneous proposal acceptances and milestone payouts using `SELECT ... FOR UPDATE` and `@Version` annotations.
* **Idempotency Guarantee**: Interceptors cache and verify `Idempotency-Key` request headers to ensure retried HTTP requests never trigger duplicate charges or disbursements.
* **Testcontainers Integration Testing**: Runs integration test suites against real, ephemeral PostgreSQL and Redis Docker containers rather than synthetic in-memory mocks (H2).
* **RFC 7807 Problem Details**: Standardized REST error envelopes across all validation, state transition, and security exceptions.
* **Production Observability**: Spring Boot Actuator endpoints, Micrometer Prometheus metrics, and correlation IDs (`MDC`) for distributed tracing.

### Track 2: Commercial Viability & Monetization (The Money-Maker)
* **Stripe Escrow Payment Rails**: Clients lock bounty rewards into platform escrow upfront before developers invest engineering time—eliminating payment default risks.
* **Automated Take-Rate Commission**: Automatically extracts a 10%–15% platform take-rate fee upon milestone delivery approval.
* **Ghosting Protection & Auto-Release**: Background `@Scheduled` timer auto-releases escrow funds to the developer after 14 calendar days of client inactivity on submitted deliverables.
* **Dispute Arbitration**: Clear legal and administrative dispute states (`DISPUTED`) enabling platform arbitrators to review code deliverables and issue binding payouts or refunds.
* **Algorithmic Reputation**: Dynamic developer and client scoring based on on-time delivery rates, peer review averages, and completion history.

---

## 📚 Complete Technical Documentation Suite

For deep architectural insights, database designs, API specifications, and operational guides:

| Document | Focus Area | Description |
| :--- | :--- | :--- |
| [🚀 Zero-to-Launch Guide (`DEVELOPMENT_TO_LAUNCH_GUIDE.md`)](DEVELOPMENT_TO_LAUNCH_GUIDE.md) | **Dual-Track Playbook** | End-to-end execution guide, tech interview talking points, and GTM strategy. |
| [📖 System Design (`SYSTEM_DESIGN.md`)](SYSTEM_DESIGN.md) | **Architecture & Flow** | System architecture, sequence diagrams, state machines, and Stripe escrow flows. |
| [🗺 Development Roadmap (`ROADMAP.md`)](ROADMAP.md) | **Engineering Phases** | 14-phase roadmap tracking completed modules and upcoming financial/hardening phases. |
| [🔌 API Specification (`API_SPECIFICATION.md`)](API_SPECIFICATION.md) | **API Contracts** | Exhaustive REST contracts, JSON schemas, headers, query params & RFC 7807 error models. |
| [🗄 Database Schema (`DATABASE_SCHEMA.md`)](DATABASE_SCHEMA.md) | **Persistence** | PostgreSQL relational schema, DDL, constraints, indexing strategies, and data dictionary. |
| [🔐 Security Architecture (`SECURITY_MODEL.md`)](SECURITY_MODEL.md) | **Auth & Access** | Stateless JWT authentication, RBAC permission matrix, password hashing & OWASP mitigations. |
| [🏛 Architecture Decisions (`ARCHITECTURE_DECISIONS.md`)](ARCHITECTURE_DECISIONS.md) | **Trade-off Records** | ADRs documenting trade-offs (Monolith, Concurrency, Ledger, Testcontainers). |
| [🐳 Deployment & Operations (`DEPLOYMENT.md`)](DEPLOYMENT.md) | **DevOps & Cloud** | Multi-stage Docker containerization, `docker-compose.yml`, JVM tuning, and monitoring. |
| [🤝 Contributing Guide (`CONTRIBUTING.md`)](CONTRIBUTING.md) | **Workflow** | Contribution standards, Git workflow, branch naming, and PR checklist. |
| [🤖 Agent & AI Guidelines (`AGENT.md`)](AGENT.md) | **Co-Pilot Rules** | Senior engineering co-pilot and pair-programming architecture principles. |

---

## 🛠 Tech Stack

- **Language:** Java 21 (LTS)
- **Framework:** Spring Boot 3.3.3
  - Spring Web (RESTful APIs)
  - Spring Data JPA (Hibernate ORM)
  - Spring Security (Stateless JWT Auth with BCrypt hashing)
  - Spring Boot Actuator (Health, Liveness & Prometheus Metrics)
  - Spring Boot Validation (Jakarta Validation)
- **Database:** PostgreSQL 16 (Production) / Testcontainers (Integration Testing)
- **Financial Rails:** Stripe Connect API (Escrow, Payouts, Webhook Signatures)
- **Documentation:** SpringDoc OpenAPI 2.6.0 (Swagger UI)
- **Utilities:** Lombok, Dotenv Java
- **Containerization:** Docker & Docker Compose
- **Build Tool:** Maven 3.9+

---

## 🏗 High-Level Architecture

```text
[ Client (Web / Mobile / CLI) ]
               │ HTTPS Requests (REST API)
               ▼
[ Cloudflare WAF & DDoS Shield ]
               │
               ▼
[ JwtAuthenticationFilter & SecurityFilterChain ]
               │ Authenticated & Authorized Requests
               ▼
[ Controller Layer (@RestController) ] ── (Jakarta @Valid DTOs, RFC 7807 problem details)
               │
               ▼
[ Service Layer (@Service) ] ─────────── (Business domain logic, state transitions, @Transactional)
         │                   │                         │
         ▼                   ▼                         ▼
[ Stripe Escrow Engine ] [ Double-Entry Ledger ] [ Inactivity Auto-Release Guard ]
         │                   │                         │
         ▼                   ▼                         ▼
[ Stripe Webhooks ]      [ Immutable Audit Trail ]  [ @Scheduled Workers ]
               │
               ▼
[ Repository Layer (@Repository) ] ───── (Spring Data JPA, Pessimistic Locking, Projections)
               │
               ▼
[ PostgreSQL Database ] ──────────────── (Relational tables, foreign keys, check constraints)
```

---

## 🌿 Repository Branch Strategy & Structure

OpenBounty utilizes a clean separation of concerns across branches, unified in `main`:

| Branch | Description | Contents |
| :--- | :--- | :--- |
| **`backend`** | Core Spring Boot REST API service | Tracks `backend/` directory only |
| **`frontend`** | Web client interface portal | Tracks `frontend/` directory only |
| **`main`** | Unified deployment & integration testing | Houses both `backend/` & `frontend/` with Docker Compose |

```text
OpenBounty/
├── .github/workflows/ci.yml   # CI/CD pipeline
├── backend/                   # Spring Boot 3.3 + Java 21 REST API
│   ├── src/
│   ├── Dockerfile
│   ├── pom.xml
│   └── README.md
├── frontend/                  # Modern Web Client Portal
│   ├── src/
│   ├── public/
│   ├── package.json
│   └── README.md
├── docker-compose.yml         # Full-stack container orchestration
├── .gitignore                 # Root and module git ignore rules
└── README.md
```

---

## ⚡ Quick Start

### 1. Prerequisites
- **JDK 21** installed and configured in your `PATH`
- **Maven 3.9+**
- **Docker & Docker Compose**

### 2. Clone the Repository
```bash
git clone https://github.com/gargnikunj991-ux/OpenBounty.git
cd OpenBounty
```

### 3. Local Backend Setup
```bash
# 1. Start PostgreSQL container
docker compose up -d postgres

# 2. Configure backend environment
cd backend
cp .env.example .env

# 3. Build & Run Spring Boot application
mvn spring-boot:run
```

The API will start on `http://localhost:8080`.

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

All rights reserved. Licensing terms to be determined upon public release.
