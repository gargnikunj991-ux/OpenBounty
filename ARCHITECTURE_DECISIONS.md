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
