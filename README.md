# 🚀 OpenBounty — High-Concurrency Escrow & Bounty Platform Backend

[![CI Pipeline](https://github.com/gargnikunj991-ux/OpenBounty/actions/workflows/ci.yml/badge.svg)](https://github.com/gargnikunj991-ux/OpenBounty/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-brightgreen.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)
![Redis](https://img.shields.io/badge/Redis-7.2-red.svg)
![Docker](https://img.shields.io/badge/Docker-Enabled-blue.svg)
![Testcontainers](https://img.shields.io/badge/Testcontainers-1.20-blueviolet.svg)

**OpenBounty** is an enterprise-grade, escrow-backed challenge and bounty backend system designed to connect **Clients/Organizations** who post technical challenges with **Developers/Solvers** who propose, build, and deliver milestone-verified solutions.

Architected specifically as a **senior backend engineering portfolio showcase**, OpenBounty emphasizes deep systems-level challenges: **high-concurrency race condition prevention**, **GAAP-compliant double-entry financial ledger accounting**, **distributed idempotency guarantees**, **token-bucket rate limiting**, **Flyway database migrations**, and **ephemeral Testcontainers integration testing**.

---

## 🎯 Architectural Highlights & Backend Engineering Signals

```text
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              OPENBOUNTY BACKEND ENGINEERING CAPABILITIES                               │
├────────────────────────────────────┬───────────────────────────────────────────────────────────────────┤
│ 🔒 CONCURRENCY & INTEGRITY          │ 📒 FINANCIAL & PERSISTENCE RIGOR                                  │
│ • Pessimistic row locking (FOR UPDATE)│ • In-house Double-Entry Bookkeeping Ledger (GAAP balanced)      │
│ • TOCTOU race condition elimination │ • Immutable audit journal (DEBIT / CREDIT balance invariant)     │
│ • Atomic multi-proposal rejections  │ • Flyway versioned SQL migrations (schema evolution safety)       │
│ • Distributed Idempotency-Key engine│ • Zero-allocation JPQL projections & N+1 query elimination        │
├────────────────────────────────────┼───────────────────────────────────────────────────────────────────┤
│ ⚡ SCALE & PRODUCTION RESILIENCE    │ 🛡️ OBSERVABILITY & ENTERPRISE TESTING                              │
│ • Redis distributed caching layer   │ • Spring Boot Actuator & Prometheus metrics (/actuator/prometheus)│
│ • Bucket4j Token-Bucket rate limit  │ • Structured JSON logging with MDC correlation trace IDs         │
│ • Stateless JWT + Refresh Token RTR │ • Real ephemeral Docker Testcontainers (PostgreSQL 16 & Redis)   │
│ • RFC 7807 Global Problem Details   │ • Multi-threaded adversarial concurrency stress test suites       │
└────────────────────────────────────┴───────────────────────────────────────────────────────────────────┘
```

### 1. Concurrency Control & Race Condition Prevention
* **Pessimistic Write Locking (`SELECT ... FOR UPDATE`)**: Applied during proposal acceptance and milestone payout transitions to prevent double-assignments and duplicate disbursements under concurrent requests.
* **Bulk Atomic State Transitions**: Atomically assigns the winning developer while batch-rejecting competing bids in a single ACID transaction, preventing Time-of-Check to Time-of-Use (TOCTOU) inconsistencies.

### 2. Double-Entry Bookkeeping Financial Ledger
* **In-House Escrow & Ledger Accounting**: Replaces naive column decrementing with an immutable ledger (`ledger_entries`).
* **Balanced Invariants**: Enforces strict mathematical balance across ledger accounts (`PLATFORM_CASH`, `CLIENT_ESCROW_LOCKED`, `DEVELOPER_PAYABLE`, `PLATFORM_FEE_REVENUE`) where $\sum \text{Debit} \equiv \sum \text{Credit}$, eliminating phantom money creation or balance drift.

### 3. Distributed Idempotency Key Engine
* **Replay & Retry Safety**: Mutating endpoints inspect the `Idempotency-Key` HTTP header. Request payload SHA-256 digests and response statuses are atomically verified to return deterministic cached results upon client or network retries.

### 4. Distributed Caching & Rate Limiting (Redis & Bucket4j)
* **High-Throughput Caching**: Redis caches high-traffic read operations (bounty catalog, public user stats) with explicit eviction upon domain mutations.
* **Token-Bucket Rate Limiting**: Distributed Bucket4j token bucket limits prevent brute-force authentication attempts and proposal spamming.

### 5. Production Observability & Telemetry
* **Metrics & Tracing**: Micrometer integration with `/actuator/prometheus` scraping endpoint exposing custom business timers and counters (e.g. `bounties.funded.total`, `payouts.disbursed.duration`).
* **Structured Distributed Tracing**: Request interceptors inject `X-Correlation-ID` into Logback MDC, propagating through logs and outgoing RFC 7807 response headers.

### 6. Rigorous Integration Testing via Testcontainers
* **Zero Synthetic Mocks**: Integration tests run against real, ephemeral PostgreSQL 16 and Redis Docker containers via `@Testcontainers`, verifying exact dialect behavior, native constraints, and locking semantics under load.

---

## 💼 Resume-Ready Project Bullet Points (STAR Format)

> * **OpenBounty | High-Concurrency Escrow & Bounty Backend Platform** *(Java 21, Spring Boot 3.3, PostgreSQL 16, Redis, Docker, Testcontainers)*
>   * Architected a high-concurrency escrow marketplace backend in **Java 21** and **Spring Boot 3.3**, enforcing strict state transitions across challenges, proposals, and deliverables.
>   * Eliminated TOCTOU race conditions and double-assignment defects during concurrent proposal evaluations using database-level pessimistic locking (`SELECT ... FOR UPDATE`) and atomic bulk updates.
>   * Designed a **GAAP-compliant double-entry financial ledger** enforcing immutable debit/credit invariants and distributed `Idempotency-Key` verification to guarantee zero financial drift on network retries.
>   * Integrated **Redis distributed caching** and **Bucket4j token-bucket rate limiting**, safeguarding sensitive mutation endpoints and optimizing read query latency to sub-50ms p99.
>   * Standardized API error envelopes with **RFC 7807 Problem Details** and established production telemetry using **Spring Boot Actuator**, **Micrometer Prometheus metrics**, and **MDC distributed tracing**.
>   * Built comprehensive end-to-end integration test suites with **Testcontainers** (ephemeral PostgreSQL 16 & Redis containers) and multi-threaded stress tests to validate concurrency safety under synthetic load.

---

## 🛠 Tech Stack

- **Language:** Java 21 (LTS)
- **Framework:** Spring Boot 3.3.3
  - Spring Web (RESTful API architecture)
  - Spring Data JPA (Hibernate 6 ORM, Projections, `@EntityGraph`)
  - Spring Security 6 (Stateless JWT, Refresh Token Rotation, BCrypt 12)
  - Spring Boot Actuator (Health, Liveness & Prometheus Metrics)
  - Jakarta Validation (Bean Validation 3.0)
- **Data Persistence & Caching:**
  - PostgreSQL 16 (Relational storage, B-Tree indexes, JSONB)
  - Redis 7.2 (Distributed caching & Token Bucket rate limiting)
  - Flyway (Version-controlled database schema migrations)
- **API Standards & Documentation:**
  - RFC 7807 Global Problem Details error envelope
  - SpringDoc OpenAPI 2.6.0 (Swagger UI & OpenAPI 3.0 spec)
- **Testing & Quality Assurance:**
  - Testcontainers 1.20 (PostgreSQL & Redis ephemeral containers)
  - JUnit 5, Mockito & Spring MockMvc
- **Build & Containerization:**
  - Maven 3.9+
  - Multi-stage Docker & Docker Compose

---

## 🏗 High-Level System Architecture

```text
[ Client (Frontend Web / Mobile / Third-Party Consumer) ]
                         │ HTTPS (RESTful Requests)
                         ▼
[ Cloudflare WAF / Reverse Proxy ]
                         │
                         ▼
[ RateLimitingFilter (Bucket4j + Redis) ] ── (Token Bucket Rate Limiting)
                         │
                         ▼
[ JwtAuthenticationFilter & SecurityFilterChain ] ── (Stateless JWT + RTR)
                         │
                         ▼
[ IdempotencyKeyInterceptor ] ── (Caches & validates Idempotency-Key hashes)
                         │
                         ▼
[ Controller Layer (@RestController) ] ── (Jakarta @Valid DTOs, RFC 7807 Errors)
                         │
                         ▼
[ Service Layer (@Service) ] ── (State Machine, Concurrency Locks, Business Rules)
          │                   │                         │
          ▼                   ▼                         ▼
[ Double-Entry Ledger ] [ Inactivity Auto-Release ] [ Distributed Redis Cache ]
(Immutable Debit/Credit)  (@Scheduled Worker)       (Query Speedup & Eviction)
                         │
                         ▼
[ Repository Layer (@Repository) ] ── (Spring Data JPA, Pessimistic Locking, Projections)
                         │
                         ▼
[ PostgreSQL 16 Database ] ── (Flyway Migrations, Foreign Keys, B-Tree Indexes)
```

---

## 📚 Complete Technical Documentation Suite

| Document | Focus Area | Description |
| :--- | :--- | :--- |
| [📖 System Design (`SYSTEM_DESIGN.md`)](SYSTEM_DESIGN.md) | **Architecture & Flow** | System architecture, ER diagrams, state machines, and concurrency locking flows. |
| [🗺 Development Roadmap (`ROADMAP.md`)](ROADMAP.md) | **Engineering Phases** | Engineering phases tracking completed milestones and remaining backend hardening. |
| [🔌 API Specification (`API_SPECIFICATION.md`)](API_SPECIFICATION.md) | **API Contracts** | Complete REST contracts, JSON schemas, headers, query params & RFC 7807 error models. |
| [🗄 Database Schema (`DATABASE_SCHEMA.md`)](DATABASE_SCHEMA.md) | **Persistence** | PostgreSQL relational schema, DDL, constraints, indexing strategies, and data dictionary. |
| [🔐 Security Architecture (`SECURITY_MODEL.md`)](SECURITY_MODEL.md) | **Auth & Access** | Stateless JWT authentication, RBAC permission matrix, password hashing & OWASP mitigations. |
| [🏛 Architecture Decisions (`ARCHITECTURE_DECISIONS.md`)](ARCHITECTURE_DECISIONS.md) | **Trade-off Records** | ADRs documenting trade-offs (Modular Monolith, Concurrency, Ledger, Testcontainers). |
| [🚀 Engineering Playbook (`DEVELOPMENT_TO_LAUNCH_GUIDE.md`)](DEVELOPMENT_TO_LAUNCH_GUIDE.md) | **Interview & Architecture** | In-depth technical interview defense guide, systems design Q&A, and benchmark playbook. |
| [🐳 Deployment & Operations (`DEPLOYMENT.md`)](DEPLOYMENT.md) | **DevOps & Cloud** | Multi-stage Docker containerization, `docker-compose.yml`, JVM tuning, and monitoring. |

---

## ⚡ Quick Start & Local Execution

### 1. Prerequisites
- **JDK 21** installed and configured in your `PATH`
- **Maven 3.9+**
- **Docker & Docker Compose**

### 2. Clone the Repository
```bash
git clone https://github.com/gargnikunj991-ux/OpenBounty.git
cd OpenBounty
```

### 3. Spin Up Infrastructure with Docker Compose
```bash
# Starts PostgreSQL 16 and Redis containers
docker compose up -d postgres redis
```

### 4. Configure & Run Backend
```bash
cd backend
cp .env.example .env

# Build & Run Spring Boot application
mvn spring-boot:run
```

The service will boot on `http://localhost:8080`.

---

## 📚 Interactive API Documentation & Telemetry

Once the service is active:
- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3.0 Spec:** [http://localhost:8080/api-docs](http://localhost:8080/api-docs)
- **Health & Liveness Check:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Prometheus Metrics:** [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)

---

## 🧪 Testing & Verification

Execute the comprehensive unit, integration, and adversarial concurrency test suites:
```bash
mvn clean test
```

---

## 📄 License

MIT License. Designed and maintained as an open engineering portfolio project.
