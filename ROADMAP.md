# OpenBounty — 10-Phase Development Roadmap

This document outlines the step-by-step engineering roadmap to build the **OpenBounty** platform from scratch to production-ready deployment.

---

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   DEVELOPMENT ROADMAP                                       │
│                                                                                             │
│  [Phase 1] Setup & Config  ──►  [Phase 2] Domain Entities  ──►  [Phase 3] Repositories      │
│                                                                        │                    │
│  [Phase 6] Bounty Module   ◄──  [Phase 5] Spring Security  ◄──  [Phase 4] DTOs & Exceptions │
│         │                                                                                   │
│         ▼                                                                                   │
│  [Phase 7] Proposals       ──►  [Phase 8] Milestones       ──►  [Phase 9] Analytics         │
│                                                                        │                    │
│                                 [Phase 10] Testing, Swagger, Docker ◄──┘                    │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Project Setup & Environment Configuration
* **Goal**: Establish the foundational Spring Boot 3.3+ (Java 21) workspace.
* **Key Deliverables**:
  1. `pom.xml` with dependencies (Web, Data JPA, Security, Validation, PostgreSQL, JWT, Lombok, Swagger, Test).
  2. Standard package structure (`config`, `controller`, `dto`, `entity`, `enums`, `exception`, `repository`, `service`).
  3. `application.yml` environment configuration (PostgreSQL datasource, JPA ddl-auto, JWT secret, Swagger paths).
* **Engineering Concept**: Convention over configuration, 12-factor application config via environment variables.

---

## Phase 2: Domain Modeling & Database Schema (Entities & Enums)
* **Goal**: Model the core relational schema using JPA annotations.
* **Key Deliverables**:
  1. Enums: `Role`, `BountyCategory`, `BountyStatus`, `ProposalStatus`, `MilestoneStatus`.
  2. JPA Entities:
     * `User` (`users` table, unique email, password hash, role, reputation score).
     * `Bounty` (`bounties` table, foreign keys to client and assigned developer, reward amount, category).
     * `Proposal` (`proposals` table, foreign keys to bounty and developer, proposed amount, estimated days).
     * `Milestone` (`milestones` table, deliverable URL, status, submission timestamps).
     * `Review` (`reviews` table, 1-5 rating, feedback text).
* **Engineering Concept**: JPA relationships (`@ManyToOne`, `@OneToMany`), `EnumType.STRING`, `@CreationTimestamp`, `@UpdateTimestamp`, indexing.

---

## Phase 3: Data Access Layer (Spring Data JPA Repositories)
* **Goal**: Build optimized database query interfaces.
* **Key Deliverables**:
  1. `UserRepository`: `findByEmail(String email)`, `existsByEmail(String email)`.
  2. `BountyRepository`: Filtering by status, category, keyword search, pagination (`Pageable`).
  3. `ProposalRepository`: Find proposals by bounty, find proposals by developer, check existing submissions.
  4. `MilestoneRepository`: Fetch milestones ordered by sequence.
  5. `ReviewRepository`: Calculate average rating and reputation.
* **Engineering Concept**: Derived queries, JPQL custom queries, Pagination (`Page<T>`) to prevent memory overload.

---

## Phase 4: DTO Layer, Request Validation & Centralized Error Handling
* **Goal**: Decouple the API contract from the database entities and establish robust error handling.
* **Key Deliverables**:
  1. Request DTOs with Jakarta Bean Validation (`@NotBlank`, `@Size`, `@Min`, `@Email`).
  2. Response DTOs to hide sensitive fields (passwords) and prevent circular JSON loops.
  3. Custom Business Exceptions (`ResourceNotFoundException`, `BadRequestException`, `UnauthorizedException`, `InvalidStateTransitionException`).
  4. Global Exception Handler (`@RestControllerAdvice`) returning standardized RFC 7807 problem details.
* **Engineering Concept**: Input sanitization, Defensive programming, Separation of API contract from persistence model.

---

## Phase 5: Authentication & Stateless Security (Spring Security 6 + JWT)
* **Goal**: Implement secure, stateless token-based authentication and Role-Based Access Control (RBAC).
* **Key Deliverables**:
  1. `PasswordEncoder` bean using BCrypt hashing.
  2. `JwtService` for token generation, claims extraction, and signature validation.
  3. `JwtAuthenticationFilter` (`OncePerRequestFilter`) to intercept requests and populate `SecurityContextHolder`.
  4. `SecurityFilterChain` bean configuring CORS, CSRF disable, public vs protected routes, and session policy `STATELESS`.
  5. `AuthController` & `AuthService`:
     * `POST /api/auth/register` (Register as `ROLE_CLIENT` or `ROLE_DEVELOPER`).
     * `POST /api/auth/login` (Verify credentials and return JWT token).
     * `GET /api/auth/me` (Fetch authenticated user profile).
* **Engineering Concept**: Stateless authentication, JWT claims, Password hashing, Spring Security filter chain lifecycle.

---

## Phase 6: Bounty / Challenge Management Module
* **Goal**: Implement the core bounty lifecycle APIs.
* **Key Deliverables**:
  1. `POST /api/bounties`: Create a new bounty (`ROLE_CLIENT` only).
  2. `GET /api/bounties`: Search, filter by category/status, and paginate open bounties (Public).
  3. `GET /api/bounties/{id}`: Detailed view of a single bounty (Public).
  4. `PATCH /api/bounties/{id}/cancel`: Cancel bounty with state validation (`ROLE_CLIENT` owner only).
* **Engineering Concept**: Role authorization (`@PreAuthorize`), pagination/sorting, state machine guards.

---

## Phase 7: Proposal & Bidding Lifecycle Module
* **Goal**: Allow developers to submit solution proposals and clients to review/accept them.
* **Key Deliverables**:
  1. `POST /api/bounties/{id}/proposals`: Submit solution proposal with estimated timeline (`ROLE_DEVELOPER` only).
  2. `GET /api/bounties/{id}/proposals`: View all proposals submitted for a bounty (`ROLE_CLIENT` owner only).
  3. `PATCH /api/proposals/{id}/accept`: Atomic transaction (`@Transactional`) that:
     * Accepts selected proposal (`ACCEPTED`).
     * Rejects competing proposals (`REJECTED`).
     * Assigns developer to bounty and updates bounty status to `ASSIGNED`.
  4. `PATCH /api/proposals/{id}/reject`: Explicitly reject a proposal.
* **Engineering Concept**: ACID transactions (`@Transactional`), race condition prevention, multi-entity state updates.

---

## Phase 8: Milestone Tracking & Deliverable Verification Module
* **Goal**: Break projects into deliverables and verify completion.
* **Key Deliverables**:
  1. Milestone creation and progress breakdown.
  2. `POST /api/milestones/{id}/submit`: Submit deliverable proof (GitHub link, live demo, documentation) by assigned developer.
  3. `PATCH /api/milestones/{id}/approve`: Client approves milestone.
  4. Automatic completion trigger: When 100% of milestones are approved, transition bounty to `COMPLETED` and trigger reward disbursement.
* **Engineering Concept**: Workflow automation, deliverable verification, progress calculation.

---

## Phase 9: Reviews, Reputation System & Analytics Dashboard APIs
* **Goal**: Build social proof, rating mechanics, and high-level platform insights.
* **Key Deliverables**:
  1. `POST /api/reviews`: Submit rating (1-5) and feedback upon bounty completion.
  2. Dynamic reputation score calculation for developers and clients.
  3. Analytics APIs:
     * `GET /api/analytics/overview`: High-level metrics (total bounties, funds paid, active developers).
     * `GET /api/analytics/categories`: Breakdown of challenges by domain/category.
* **Engineering Concept**: Aggregation queries (SQL `COUNT`, `SUM`, `AVG`), database performance optimization.

---

## Phase 10: Automated Testing, Documentation, Docker & Production Readiness
* **Goal**: Ensure the system is robust, documented, and deployable anywhere.
* **Key Deliverables**:
  1. Unit tests with JUnit 5 & Mockito (`@ExtendWith(MockitoExtension.class)`).
  2. Integration tests with MockMvc (`@SpringBootTest`, `@AutoConfigureMockMvc`).
  3. Interactive API documentation via OpenAPI 3.0 / Swagger UI (`/swagger-ui.html`).
  4. Multi-stage `Dockerfile` and `docker-compose.yml` (Spring Boot + PostgreSQL).
* **Engineering Concept**: Test-driven verification, CI/CD readiness, containerization.
