# OpenBounty — Architecture Decision Records (ADRs)

This document records the foundational architectural decisions made for the **OpenBounty** platform, capturing context, options considered, decisions, and consequences.

---

## ADR-001: Architecture Style — Modular Monolith over Microservices

### Context
OpenBounty is a collaborative challenge and milestone platform requiring transactional consistency across user profiles, bounties, proposals, milestones, and reviews. We needed to choose an architectural topology that enables rapid feature velocity, low operational complexity, and strict ACID transaction guarantees.

### Options Considered
1. **Microservices Architecture**: Separate services for Auth, Bounties, Proposals, Milestones, and Analytics communicating via gRPC/Kafka.
2. **Modular Monolith (Spring Boot 3.3)**: A unified codebase organized into strictly separated domain modules with clean internal interfaces.

### Decision
Adopt a **Modular Monolith** architecture with Spring Boot 3.3.

### Consequences
* **Positive**:
  - Direct relational ACID transactions across entities without 2-phase commit (2PC) or Saga orchestrator overhead.
  - Simplified local development, unified CI/CD pipeline, and single-container deployment.
  - Sub-millisecond in-process service invocations with zero network latency between modules.
* **Trade-offs / Mitigations**:
  - Requires disciplined package boundaries (`model`, `repository`, `service`, `controller`, `dto`) to prevent tight coupling if extraction into microservices is needed in the future.

---

## ADR-002: Authentication — Stateless JWT over Server-Side Sessions

### Context
We required an authentication mechanism that works cleanly across web frontends, mobile clients, and third-party API consumers without binding server memory to active user sessions.

### Options Considered
1. **Stateful Server-Side HTTP Sessions** stored in Redis or in-memory servlet container.
2. **Stateless JSON Web Tokens (JWT)** signed via HMAC-SHA256.

### Decision
Adopt **Stateless JWT Authentication**.

### Consequences
* **Positive**:
  - Horizontal scalability: backend application instances can scale up/down with zero session synchronization required.
  - Decoupled API architecture: clients pass `Authorization: Bearer <token>` with standard REST requests.
  - Reduced database I/O on read-heavy endpoints since user identity and roles are verified cryptographically in memory.
* **Trade-offs / Mitigations**:
  - Inability to instantly invalidate individual tokens before expiration without a distributed token revocation blacklist (e.g. Redis cache for revoked tokens). Standard 24h expiration mitigates replay risk.

---

## ADR-003: Persistence & ORM — PostgreSQL with Spring Data JPA

### Context
The OpenBounty data model consists of highly relational data: users, challenges, proposals, deliverables, and ratings with strict foreign keys and state constraints.

### Options Considered
1. **MongoDB / NoSQL Document Store**: Document model for bounties with embedded proposals.
2. **PostgreSQL 15+ with Spring Data JPA & Hibernate**: Relational ACID database with strong typing.

### Decision
Adopt **PostgreSQL 15+** with **Spring Data JPA**.

### Consequences
* **Positive**:
  - Enforced relational integrity (Foreign Keys, Unique constraints, Check constraints).
  - Powerful analytical capabilities (`AVG`, `COUNT`, `SUM`, filtering, and pagination) via standard SQL and JPQL.
  - High developer productivity with Spring Data derived queries and repository abstractions.
* **Trade-offs / Mitigations**:
  - Care must be taken to configure `FetchType.LAZY` on relationships to avoid N+1 query problems.

---

## ADR-004: Concurrency & State Management — Atomic Proposal Acceptance

### Context
When a client accepts a proposal for a bounty, the system must atomically:
1. Transition the accepted proposal to `ACCEPTED`.
2. Reject all other competing proposals (`REJECTED`).
3. Assign the winning developer to the bounty.
4. Transition bounty status to `ASSIGNED`.

If any step fails or concurrent requests occur, the bounty state must not be corrupted or double-assigned.

### Decision
Implement the acceptance workflow inside a single `@Transactional` method with database-level isolation and entity state validation guards.

### Consequences
* **Positive**:
  - Eliminates race conditions and guarantees all-or-nothing database consistency.
  - Ensures a bounty cannot have more than one winning proposal or assigned developer.

---

## ADR-005: Error Handling Contract — RFC 7807 Standardized Problem Details

### Context
Client applications require a predictable, machine-readable format for all HTTP errors (`400`, `401`, `403`, `404`, `409`, `500`).

### Decision
Adopt **RFC 7807 Problem Details for HTTP APIs** using Spring Boot `@RestControllerAdvice`.

### Consequences
* **Positive**:
  - Standardized JSON error response across all modules containing `type`, `title`, `status`, `detail`, `instance`, and `timestamp`.
  - Nested validation error map (`errors: { field: message }`) for granular client-side form feedback.

---

## ADR-006: Strategic Direction — Dual-Track Engineering & Commercial Architecture

### Context
When building OpenBounty, we evaluated two divergent project paths:
1. **Academic Portfolio Project**: Focus solely on theoretical design patterns, mocking external services, with zero real-world commercial viability.
2. **Speed-First Startup MVP**: Hack together third-party no-code tools or quick scripts to get payments working, compromising code quality, concurrency safety, and testing.

### Decision
Adopt a **Dual-Track Architecture (Production-Grade Hybrid)**:
* Construct the system as a **commercially viable marketplace** (automated Stripe Connect escrow, 10–15% platform take-rate, dispute protocol, GitHub issue bot integration).
* Architect the implementation with **enterprise-grade engineering rigor** (immutable double-entry ledger, pessimistic concurrency locking, idempotency keys, and real Testcontainers testing).

### Consequences
* **Positive**:
  - Delivers maximum career leverage: signals senior engineering maturity to tech leads and hiring managers by solving real-world distributed state and financial problems.
  - Retains zero technical debt if deployed as a commercial revenue-generating venture.
* **Trade-offs**:
  - Requires deeper upfront design around transactional boundaries, idempotency tables, and webhook resilience.

---

## ADR-007: Financial Consistency — Double-Entry Ledger over Mutable Balances

### Context
Handling client escrow deposits, developer payouts, and platform take-rate commissions requires absolute mathematical certainty. Storing account balances as a mutable integer column (`balance = balance + X`) is vulnerable to race conditions, lost update anomalies, and provides zero audit history for dispute resolution or tax compliance.

### Decision
Implement an **Immutable Double-Entry Financial Ledger** (`ledger_entries` table).
* Every monetary transaction creates balanced `DEBIT` and `CREDIT` entries across predefined accounts (`PLATFORM_CASH`, `CLIENT_ESCROW_LOCKED`, `DEVELOPER_PAYABLE`, `PLATFORM_FEE_REVENUE`).
* Enforce the fundamental accounting invariant: `SUM(debit_amount) == SUM(credit_amount)`.

### Consequences
* **Positive**:
  - Mathematical impossibility of phantom balance creation or untracked money leaks.
  - Complete, tamper-proof GAAP-compliant audit log for every transaction, refund, or payout split.
  - Direct alignment with fintech engineering standards expected in senior backend roles.
* **Trade-offs**:
  - Querying current account balances requires aggregation (`SUM(debits) - SUM(credits)`), mitigated by indexed account queries and periodic balance snapshots if scale demands.

---

## ADR-008: Concurrency & Idempotency — Pessimistic Row Locking & Idempotency Keys

### Context
In a bounty marketplace, critical race conditions can occur:
1. Two proposal actions or simultaneous client clicks could attempt to accept multiple competing proposals or disburse milestone payouts concurrently.
2. Flaky mobile/network connections might cause clients to retry HTTP POST/PATCH requests, potentially resulting in duplicate payments.

### Decision
1. Apply **Pessimistic Database Row Locking** (`LockModeType.PESSIMISTIC_WRITE` / `SELECT ... FOR UPDATE`) on bounty and milestone rows during state-changing operations.
2. Implement an **Idempotency Engine**: require an `Idempotency-Key` header on financial mutation endpoints, caching the SHA-256 request payload hash and response status to return deterministic cached responses on retries.

### Consequences
* **Positive**:
  - Absolute protection against double-spending and multiple winning developer assignments.
  - Clean idempotency handling conforming to Stripe-level payment engineering standards.
* **Trade-offs**:
  - Database row locks must be held for minimal duration to avoid database connection pool exhaustion. Long external HTTP calls (e.g. Stripe API calls) must occur outside the locked database transaction.

---

## ADR-009: Integration Testing — Testcontainers over In-Memory H2

### Context
In-memory H2 databases fail to accurately replicate production PostgreSQL behavior:
* Native JSONB column operators and indexing.
* Row-level locking behavior under concurrency (`SELECT ... FOR UPDATE`).
* Case-sensitivity, constraints, and specific PostgreSQL time functions.

### Decision
Standardize all integration test suites on **Testcontainers** (`org.testcontainers:postgresql`).

### Consequences
* **Positive**:
  - 100% parity between local test suites, CI/CD pipeline runs, and production PostgreSQL 16 managed databases.
  - Eliminates "works on H2, fails on Postgres" production defects.
* **Trade-offs**:
  - Test execution requires an active Docker daemon and adds a few seconds of initial container startup overhead.

