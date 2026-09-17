# OpenBounty — 14-Phase Dual-Track Engineering Roadmap

This document outlines the master engineering roadmap to transform **OpenBounty** from core architecture into an enterprise-grade, escrow-backed, revenue-generating marketplace platform — balancing **Track 1 (Resume & Senior Engineering Signals)** with **Track 2 (Commercial Monetization & Stripe Escrow)**.

---

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       DUAL-TRACK ROADMAP TOPOLOGY                                       │
│                                                                                                         │
│  [Phase 1] Setup & Config   ──► [Phase 2] Domain Entities   ──► [Phase 3] Repositories                  │
│  (Foundation)                   (Foundation)                    (Pessimistic Locking / Resume)          │
│                                                                         │                               │
│  [Phase 6] Bounty Module    ◄── [Phase 5] Spring Security   ◄── [Phase 4] DTOs & RFC 7807 Exceptions    │
│  (Core Product)                 (Stateless JWT / RBAC)          (API Standardization / Resume)          │
│         │                                                                                               │
│         ▼                                                                                               │
│  [Phase 7] Proposals & Bids ──► [Phase 8] Milestones & Proof ──► [Phase 9] Reviews & Reputation Score   │
│  (Atomic Concurrency / Resume)  (State Machine Delivery)        (Social Proof / Commercial Trust)       │
│                                                                         │                               │
│  [Phase 12] Double Ledger   ◄── [Phase 11] Dispute Protocol ◄── [Phase 10] Stripe Escrow & Take-Rate    │
│  (Financial Rigor / Resume)     (Auto-Release Guard)            (Core Monetization / Commercial)        │
│         │                                                                                               │
│         ▼                                                                                               │
│  [Phase 13] Testcontainers & S3►[Phase 14] Portfolio & Launch──►[LIVE PRODUCTION] Dual-Track Deployment │
│  (Real Docker Tests / DevOps)   (Interview Defense & GTM)       (Revenue + Elite Engineering Showcase)  │
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

## Phase 8: Milestone Tracking & Deliverable Verification Module 🚀
* **Status**: Next Up
* **Goal**: Break projects into verified deliverables with client review loops and auto-completion triggers.
* **Key Deliverables**:
  1. `MilestoneService` & `MilestoneController`.
  2. `POST /api/milestones/{id}/submit`: Developer submits deliverable proof (GitHub PR link, live staging URL, test results).
  3. `PATCH /api/milestones/{id}/approve`: Client reviews and approves milestone deliverable.
  4. `POST /api/milestones/{id}/request-revision`: Client requests revisions with required changes feedback.
  5. Automatic Completion Trigger: When 100% of milestones are approved, automatically transition bounty to `COMPLETED` and trigger reward release.
* **Engineering Concept**: Workflow automation, state machine progress tracking, deliverable verification.

---

## Phase 9: Reviews, Ratings & Algorithmic Reputation Engine 🚀
* **Status**: In Queue
* **Goal**: Build social proof, rating mechanics, and dynamic solver reputation scoring.
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

## Phase 10: Payment Rails, Stripe Escrow & Monetization Engine (The Money-Maker) 💰
* **Status**: Planned (Core Monetization)
* **Goal**: Integrate real fiat currency rails, automated escrow custody, and platform take-rate monetization.
* **Key Deliverables**:
  1. **Stripe Checkout / PaymentIntents (Upfront Escrow Deposit)**:
     - Clients must deposit the bounty reward upfront before the bounty transitions to `OPEN`.
     - Funds are locked securely in platform escrow.
  2. **Stripe Connect (Custom / Express)**:
     - Automated onboarding for developers to link bank accounts and complete KYC identity checks.
  3. **Platform Take-Rate (Commission Engine)**:
     - Automated **10%–15% commission** deducted on every milestone approval:
       - Example: $1,000 Milestone Approval -> $850 disbursed to developer, $150 captured as platform net revenue.
  4. **Stripe Webhook Listener**:
     - Asynchronous webhook processor for `payment_intent.succeeded`, `charge.refunded`, `transfer.created`.
     - Webhook cryptographic signature verification and idempotency protection.
* **Engineering Concept**: Escrow custody, payment gateway APIs, automated platform fee deduction, webhook reliability.

---

## Phase 11: Dispute Resolution & Inactivity Auto-Release Guard ⚖️
* **Status**: Planned
* **Goal**: Protect marketplace trust, handle client-developer disagreements, and prevent developer ghosting/non-payment.
* **Key Deliverables**:
  1. `POST /api/milestones/{id}/dispute`: Developer or client initiates dispute, instantly freezing escrow.
  2. Evidence submission: Both parties upload PR links, chat transcripts, and test artifacts.
  3. `ROLE_ADMIN` / Arbitrator Portal:
     - Administrative endpoints to review deliverable code and execute binding settlements (100% developer release, 100% client refund, or 50/50 split).
  4. **14-Day Inactivity Auto-Release Worker**:
     - Spring `@Scheduled` background worker: If a developer submits a deliverable and the client does not review or reject it within 14 calendar days, funds are automatically released to the developer.
* **Engineering Concept**: Dispute state machines, administrative override authorization, automated timeout guards.

---

## Phase 12: Double-Entry Financial Ledger & Idempotency Engine 📒
* **Status**: Planned
* **Goal**: Guarantee zero financial reconciliation errors, eliminate duplicate charges, and maintain GAAP-compliant audit trails.
* **Key Deliverables**:
  1. Immutable `ledger_entries` table recording balanced Debits and Credits.
  2. Standard Ledger Accounts:
     - `PLATFORM_CASH`
     - `CLIENT_ESCROW_LOCKED`
     - `DEVELOPER_PAYABLE`
     - `PLATFORM_FEE_REVENUE`
  3. `Idempotency-Key` HTTP Header Interceptor:
     - Caches payment operation hashes to ensure retried requests never double-charge clients or double-payout developers.
* **Engineering Concept**: Double-entry bookkeeping, ACID financial consistency, idempotency key caches.

---

## Phase 13: Testcontainers, Observability & Cloud Infrastructure 🛡️
* **Status**: Planned (Track 1: Resume & DevOps Signal)
* **Goal**: Harden the backend for high concurrency, continuous integration testing with real databases, and production monitoring.
* **Key Deliverables**:
  1. **Testcontainers Integration Test Suite**:
     - Automated MockMvc tests executing against real, ephemeral PostgreSQL 16 and Redis Docker containers (`@Testcontainers`).
     - Zero reliance on in-memory H2; tests exact locking, constraints, and JSONB queries.
  2. **Production Observability & Metrics**:
     - Spring Boot Actuator with `/actuator/prometheus` scraping endpoint.
     - Custom Micrometer counters and timers: `bounties.funded.total`, `milestones.approved.duration`, `ledger.transactions.count`.
     - Structured logging with MDC correlation IDs for end-to-end request tracing.
  3. **Flyway Database Migrations**:
     - Transition away from Hibernate `ddl-auto` to versioned, immutable SQL migrations (`V1__init.sql` through `V6__payments.sql`).
  4. **Redis Distributed Caching & Rate Limiting**:
     - Bucket4j token bucket rate limiting on auth and proposal endpoints.
     - Cache eviction strategies on public bounty search endpoints.
* **Engineering Concept**: Distributed systems testing, containerized CI/CD, production telemetry, database migration safety.

---

## Phase 14: Portfolio Presentation & Dual-Track Public Launch 🚀
* **Status**: Planned (Dual Launch)
* **Goal**: Package the platform for senior technical interview showcases while deploying a live, commercially viable marketplace.
* **Key Deliverables**:
  1. **Technical Interview & Portfolio Package**:
     - Published Architecture Decision Records (ADRs) and comprehensive system design diagrams.
     - 4 high-impact resume bullet points covering concurrency, double-entry ledger, Testcontainers, and Stripe rails.
     - Documented performance benchmarks (e.g. k6 / Locust load testing at 1,000 req/sec with p99 < 150ms).
  2. **Commercial Go-to-Market (GTM) Deployment**:
     - Cloud deployment on Render / AWS ECS with managed PostgreSQL and Cloudflare SSL/DDoS protection.
     - Live Stripe Connect sandbox-to-production cutover with webhook listeners.
     - Pre-seeding marketplace with 10–15 funded real-world open source bounties.
     - GitHub App / Bot integration allowing maintainers to fund bounties directly via `/bounty $100` on issues.
* **Engineering Concept**: Systems design defense, high-load benchmarking, commercial developer acquisition.

