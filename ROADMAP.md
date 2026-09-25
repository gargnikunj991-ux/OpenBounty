# OpenBounty — 14-Phase Backend Engineering Roadmap

This document outlines the master engineering roadmap to transform **OpenBounty** into an enterprise-grade, high-concurrency backend platform — tailored specifically as a **standout portfolio project for Senior/Mid-Senior Backend Engineer resumes and technical interviews**.

---

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   BACKEND ENGINEERING ROADMAP TOPOLOGY                                  │
│                                                                                                         │
│  [Phase 1] Setup & Config   ──► [Phase 2] Domain Entities   ──► [Phase 3] Repositories                  │
│  (Foundation)                   (JPA Relational Schema)         (Pessimistic Locking / Projections)     │
│                                                                         │                               │
│  [Phase 6] Bounty Module    ◄── [Phase 5] Spring Security   ◄── [Phase 4] DTOs & RFC 7807 Exceptions    │
│  (State Machine & Filtering)    (Stateless JWT / RTR)           (API Standardization / Error Envelopes) │
│         │                                                                                               │
│         ▼                                                                                               │
│  [Phase 7] Proposals & Bids ──► [Phase 8] Milestones & Proof ──► [Phase 9] Reviews & Reputation Engine  │
│  (Atomic Concurrency Locks)     (Deliverable Verification)      (SQL Aggregations / Weighted Scores)    │
│                                                                         │                               │
│  [Phase 12] Flyway & Redis  ◄── [Phase 11] Idempotency & Auto◄──[Phase 10] Double-Entry Ledger & Escrow │
│  (Migrations & Rate Limit)      (Replay Defense & Auto-Release) (GAAP Invariants & Debit/Credit)        │
│         │                                                                                               │
│         ▼                                                                                               │
│  [Phase 13] Testcontainers  ──► [Phase 14] Portfolio Artifacts                                         │
│  (Real Docker Tests & Telemetry)(STAR Bullets & Interview Q&A)                                          │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Project Setup & Environment Configuration ✅
* **Status**: Completed
* **Goal**: Establish the foundational Spring Boot 3.3+ (Java 21) workspace.
* **Key Deliverables**:
  1. `pom.xml` with dependencies (Web, Data JPA, Security, Validation, PostgreSQL, JWT, Lombok, Swagger, Test).
  2. Standard package structure (`config`, `controller`, `dto`, `model`, `enums`, `exception`, `repository`, `service`).
  3. `application.yml` environment configuration (PostgreSQL datasource, JPA ddl-auto, JWT secret, Swagger paths).
* **Engineering Concept**: Convention over configuration, 12-factor application config via environment variables.

---

## Phase 2: Domain Modeling & Database Schema (Entities & Enums) ✅
* **Status**: Completed
* **Goal**: Model the core relational schema using JPA annotations.
* **Key Deliverables**:
  1. Enums: `Role`, `BountyCategory`, `BountyStatus`, `ProposalStatus`, `MilestoneStatus`.
  2. JPA Entities:
     * `User` (`users` table, unique email, password hash, role, reputation score).
     * `Bounty` (`bounties` table, foreign keys to client and assigned developer, reward amount, category).
     * `Proposal` (`proposals` table, foreign keys to bounty and developer, proposed amount, estimated days).
     * `Milestone` (`milestones` table, deliverable URL, status, submission timestamps).
     * `Review` (`reviews` table, 1-5 rating, feedback text, unique participant constraint).
     * `RefreshToken` (`refresh_tokens` table, UUID token, expiry timestamp, revocation flag).
* **Engineering Concept**: JPA relationships (`@ManyToOne`, `@OneToMany`), `EnumType.STRING`, `@CreationTimestamp`, `@UpdateTimestamp`, indexing.

---

## Phase 3: Data Access Layer (Spring Data JPA Repositories) ✅
* **Status**: Completed
* **Goal**: Build optimized database query interfaces with eager loading and zero-allocation projections.
* **Key Deliverables**:
  1. `UserRepository`: Sub-millisecond indexed lookup by email (`findByEmail`), existence queries (`existsByEmail`).
  2. `BountyRepository`: Pessimistic locking (`findByIdForUpdate`), search with keyword matching, pagination (`Pageable`), category statistics aggregation (`CategoryStatsProjection`).
  3. `ProposalRepository`: Lock-free status queries (`findStatusById`), atomic competing proposal rejection (`rejectCompetingProposals`).
  4. `MilestoneRepository`: EntityGraph eager fetching (`findWithDetailsById`), sequence ordering, fast unapproved check (`existsByProposalIdAndStatusNot`).
  5. `ReviewRepository`: Aggregation queries for average rating (`calculateAverageRatingForUser`) and unique participant validation.
  6. `RefreshTokenRepository`: Pessimistic write locking on token lookup, cascade deletion by user.
* **Engineering Concept**: Derived queries, JPQL custom queries, Pagination (`Page<T>`), `@EntityGraph` N+1 query elimination.

---

## Phase 4: DTO Layer, Request Validation & Centralized Error Handling ✅
* **Status**: Completed
* **Goal**: Decouple the API contract from the database entities and establish robust RFC 7807 error handling.
* **Key Deliverables**:
  1. Request DTOs with Jakarta Bean Validation (`@NotBlank`, `@Size`, `@Min`, `@Email`, `@FutureOrPresent`).
  2. Response DTOs hiding sensitive credentials and eliminating infinite JSON recursion.
  3. Custom Business Exceptions (`ResourceNotFoundException`, `BadRequestException`, `UnauthorizedException`, `InvalidStateTransitionException`, `DuplicateResourceException`, `BountyExpiredException`, `SelfBiddingNotAllowedException`).
  4. Global Exception Handler (`@RestControllerAdvice`) returning standardized RFC 7807 problem details with database lock conflict mapping (409 Conflict).
* **Engineering Concept**: Input sanitization, Defensive programming, Separation of API contract from persistence model.

---

## Phase 5: Authentication & Stateless Security (Spring Security 6 + JWT) ✅
* **Status**: Completed
* **Goal**: Implement secure, stateless token-based authentication and Role-Based Access Control (RBAC).
* **Key Deliverables**:
  1. `PasswordEncoder` bean using BCrypt hashing with work factor 12.
  2. `JwtService` for HMAC-SHA256 token generation, claims extraction, and signature validation.
  3. `JwtAuthenticationFilter` (`OncePerRequestFilter`) to intercept requests and populate `SecurityContextHolder`.
  4. `SecurityFilterChain` bean configuring CORS, CSRF disable, public vs protected routes, and session policy `STATELESS`.
  5. `AuthController` & `AuthService`:
     * `POST /api/auth/register` (Register as `ROLE_CLIENT` or `ROLE_DEVELOPER`).
     * `POST /api/auth/login` (Verify credentials and return access JWT + refresh token).
     * `POST /api/auth/refresh` (Refresh Token Rotation with reuse detection).
     * `POST /api/auth/logout` (Revoke active session token).
     * `GET /api/auth/me` (Fetch authenticated user profile).
* **Engineering Concept**: Stateless authentication, Refresh Token Rotation (RTR), UserPrincipal per-request caching.

---

## Phase 6: Bounty / Challenge Management Module ✅
* **Status**: Completed
* **Goal**: Implement the core bounty discovery and lifecycle APIs with anti-abuse guards.
* **Key Deliverables**:
  1. `POST /api/bounties`: Create technical challenge (`ROLE_CLIENT` only) with XSS payload sanitization.
  2. `GET /api/bounties`: Filter by category, lifecycle status, and keyword matching with ORM sort field whitelisting.
  3. `GET /api/bounties/{id}`: Detailed view of a challenge with eager client profile fetching.
  4. `PATCH /api/bounties/{id}/cancel`: Cancel bounty with state validation and automatic ghost proposal cleanup.
* **Engineering Concept**: Role authorization (`@PreAuthorize`), pagination/sorting, state machine guards, XSS defense.

---

## Phase 7: Proposal & Bidding Lifecycle Module ✅
* **Status**: Completed
* **Goal**: Allow developers to submit technical bids and clients to accept winning proposals under high concurrency.
* **Key Deliverables**:
  1. `POST /api/bounties/{id}/proposals`: Submit solution proposal with milestone breakdown (`ROLE_DEVELOPER` only).
     - Strict guards: Self-bidding rejection, expired deadline rejection (410 Gone), budget cap guard.
  2. `GET /api/bounties/{id}/proposals`: View all bids submitted for a bounty (`ROLE_CLIENT` owner only).
  3. `PATCH /api/proposals/{id}/accept`: Atomic transaction with pessimistic row locking (`SELECT ... FOR UPDATE`):
     - Accepts selected proposal (`ACCEPTED`).
     - Rejects competing proposals (`REJECTED`) via bulk update.
     - Assigns developer to bounty and updates bounty status to `ASSIGNED`.
  4. `PATCH /api/proposals/{id}/reject`: Explicitly reject a proposal.
  5. Adversarial and Concurrency Test Suite covering TOCTOU races, token reuse, and IDOR attacks.
* **Engineering Concept**: Pessimistic write locking, ACID transactional integrity, deadlock prevention.

---

## Phase 8: Milestone Tracking & Deliverable Verification Module ✅
* **Status**: Completed
* **Goal**: Break projects into verified deliverables with client review loops and auto-completion triggers.
* **Key Deliverables**:
  1. `MilestoneService` & `MilestoneController`.
  2. `POST /api/milestones/{id}/submit`: Developer submits deliverable proof (GitHub PR link, live staging URL, test results).
  3. `PATCH /api/milestones/{id}/approve`: Client reviews and approves milestone deliverable with pessimistic write row lock (`SELECT ... FOR UPDATE`).
  4. `POST /api/milestones/{id}/request-revision`: Client requests revisions with required changes feedback, resetting to `PENDING`.
  5. Automatic Completion Trigger: When 100% of milestones are approved, automatically transitions bounty to `COMPLETED` and awards developer reputation points (+20).
* **Engineering Concept**: Workflow automation, sequential state machine guards (`MilestoneOrderViolationException`), IDOR access verification, auto-completion trigger.

---

## Phase 9: Reviews, Ratings & Algorithmic Reputation Engine 🚀
* **Status**: In Queue
* **Goal**: Build rating mechanics, SQL aggregations, and dynamic solver reputation scoring.
* **Key Deliverables**:
  1. `ReviewService` & `ReviewController`.
  2. `POST /api/reviews`: Submit rating (1–5) and written feedback upon bounty completion.
     - Validation: Only participants (bounty client and assigned developer) can review each other.
  3. Algorithmic Reputation Scoring:
     - Recalculates user reputation score based on on-time delivery (+20 pts), client review average, and completed bounty volume.
  4. Analytics APIs (`AnalyticsService` & `AnalyticsController`):
     * `GET /api/analytics/overview`: High-level metrics (total bounties, funds disbursed, active solvers).
     * `GET /api/analytics/categories`: Domain breakdown using `CategoryStatsProjection`.
* **Engineering Concept**: Dynamic scoring algorithms, SQL aggregation queries, marketplace social proof.

---

## Phase 10: In-House Escrow & Double-Entry Financial Ledger 📒
* **Status**: Planned (Core Backend Engineering Signal)
* **Goal**: Guarantee zero financial reconciliation errors, eliminate balance drift, and maintain GAAP-compliant audit trails.
* **Key Deliverables**:
  1. Immutable `ledger_entries` table recording balanced Debits and Credits.
  2. Standard Ledger Accounts:
     - `PLATFORM_CASH`
     - `CLIENT_ESCROW_LOCKED`
     - `DEVELOPER_PAYABLE`
     - `PLATFORM_FEE_REVENUE`
  3. Invariant Enforcer: Strict validation verifying $\sum \text{Debit} \equiv \sum \text{Credit}$ on every financial journal entry.
  4. Automated Escrow Lifecycle:
     - Locking funds on bounty publication (`CLIENT_ESCROW_LOCKED`).
     - Splitting funds upon milestone approval (Developer disbursement + Platform take-rate).
* **Engineering Concept**: Double-entry bookkeeping, ACID financial consistency, transactional invariants.

---

## Phase 11: Distributed Idempotency Engine & State Resiliency 🛡️
* **Status**: Planned
* **Goal**: Eliminate duplicate charges, prevent replay attacks, and handle client inactivity automatically.
* **Key Deliverables**:
  1. `Idempotency-Key` HTTP Header Interceptor:
     - Hashes request payloads (SHA-256) and caches execution status.
     - Returns deterministic cached responses on retried requests.
  2. 14-Day Inactivity Auto-Release Worker:
     - Spring `@Scheduled` background worker: Automatically releases escrow to the developer if a deliverable remains unreviewed for 14 calendar days.
  3. Dispute Protocol:
     - `POST /api/milestones/{id}/dispute` freezing escrow and escalating to `ROLE_ADMIN` settlement.
* **Engineering Concept**: Distributed idempotency, background job scheduling, defensive state machines.

---

## Phase 12: Production Performance: Flyway Migrations, Redis Caching & Rate Limiting ⚡
* **Status**: Planned
* **Goal**: Upgrade schema management to zero-downtime migrations and protect the system with distributed caching and rate limiting.
* **Key Deliverables**:
  1. **Flyway Versioned Migrations**:
     - Replace Hibernate `ddl-auto` with versioned, immutable SQL scripts (`V1__init.sql` through `V4__ledger.sql`).
  2. **Redis Distributed Caching**:
     - Spring Cache abstraction (`@Cacheable`, `@CacheEvict`) for high-traffic read endpoints (bounty listings, categories).
  3. **Bucket4j Distributed Rate Limiting**:
     - Token-bucket rate limiting filter preventing credential stuffing on `/api/auth/*` and spam on bid submission.
* **Engineering Concept**: Schema evolution safety, cache-aside pattern, token bucket algorithm.

---

## Phase 13: Enterprise Testing & Observability: Testcontainers & Telemetry 🧪
* **Status**: Planned (Senior SRE & DevOps Signal)
* **Goal**: Harden backend reliability with real containerized tests and production-grade observability.
* **Key Deliverables**:
  1. **Testcontainers Integration Test Harness**:
     - Automated test suites executing against real, ephemeral PostgreSQL 16 and Redis Docker containers (`@Testcontainers`).
     - Zero reliance on in-memory H2; validates native PostgreSQL locking and constraints.
  2. **Production Observability & Metrics**:
     - Spring Boot Actuator with `/actuator/prometheus` scraping endpoint.
     - Custom Micrometer counters and timers: `bounties.funded.total`, `milestones.approved.duration`, `ledger.transactions.count`.
     - Structured logging with MDC correlation IDs (`X-Correlation-ID`) for distributed tracing.
  3. **Concurrency Load Benchmarking**:
     - Reproducible k6 / JMeter load test scripts benchmarking throughput (1,000+ req/sec) and p99 latency under concurrent contention.
* **Engineering Concept**: Ephemeral container testing, production telemetry, performance benchmarking.

---

## Phase 14: Portfolio Presentation & Technical Interview Defense Guide 📄
* **Status**: Planned
* **Goal**: Package the platform for senior backend engineering resume reviews and technical interview defense.
* **Key Deliverables**:
  1. **Resume-Ready STAR Bullet Points**:
     - Polished, quantified bullets highlighting concurrency, financial ledger, caching, and Testcontainers.
  2. **System Design Deep-Dive Artifact**:
     - Complete architectural diagrams, sequence flows, and concurrency lock explanations.
  3. **Technical Interview Defense Script**:
     - Model answers for senior interview questions (e.g., handling deadlocks, isolation levels, double-entry invariants, and caching strategies).
* **Engineering Concept**: Systems design communication, engineering storytelling, interview readiness.
