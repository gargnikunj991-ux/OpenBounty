# OpenBounty — Complete System Design Document

---

## 1. Executive Summary & Dual-Track Philosophy

**OpenBounty** is an enterprise-grade, escrow-backed challenge and bounty collaboration marketplace designed to connect **Clients/Organizations** who have technical problems with **Developers/Solvers** who propose and build milestone-verified solutions.

The platform is architected around a **Dual-Track Strategy**:
1. **Track 1: Resume & Senior Engineering Signal**: Engineered to demonstrate mastery of hard backend problems—concurrency race condition prevention (`SELECT ... FOR UPDATE`), immutable double-entry financial bookkeeping, idempotent request execution, and containerized integration testing via Testcontainers.
2. **Track 2: Commercial Marketplace Viability**: Engineered as a self-sustaining business—Stripe Connect custodial escrow, automated 10–15% platform take-rate, dispute protocol, and 14-day inactivity auto-release guards.

This document outlines the end-to-end architecture, relational schema, financial escrow rails, state machines, API contracts, and security rules.

---

## 2. Stakeholders & Roles (RBAC)

1. **`ROLE_CLIENT`**:
   * Creates and funds challenges/bounties via Stripe escrow.
   * Reviews incoming proposals from developers.
   * Accepts winning proposals and assigns developers.
   * Reviews and approves milestone deliverables or requests revisions.
   * Leaves reviews and ratings upon bounty completion.

2. **`ROLE_DEVELOPER`**:
   * Explores open challenges filtered by tags, category, and reward amount.
   * Submits structured proposals with estimated timeline, bid amount, and milestone breakdown.
   * Connects bank account via Stripe Connect for automated payouts.
   * Submits deliverable URLs (GitHub PR, Live demo, Docs) for each milestone.
   * Builds public on-chain/platform reputation score upon successful completion.

3. **`ROLE_ADMIN`**:
   * Platform governance, user verification, and content moderation.
   * Arbitration panel for disputed milestones and escrow settlements.
   * Access to global financial metrics, platform gross merchandise volume (GMV), and commission analytics.

---

## 3. Database Schema & Entity-Relationship Diagram (ERD)

```mermaid
erDiagram
    USERS ||--o{ BOUNTIES : "creates (as Client)"
    USERS ||--o{ PROPOSALS : "submits (as Developer)"
    USERS ||--o{ REVIEWS : "gives/receives"
    USERS ||--o{ REFRESH_TOKENS : "owns (active sessions)"
    BOUNTIES ||--o{ PROPOSALS : "receives"
    BOUNTIES ||--o| USERS : "assigned to (Winner Dev)"
    PROPOSALS ||--o{ MILESTONES : "broken down into"
    BOUNTIES ||--o{ REVIEWS : "has"

    USERS {
        bigint id PK
        varchar name
        varchar email UK
        varchar password
        varchar role
        int reputation_score
        timestamp created_at
        timestamp updated_at
    }

    BOUNTIES {
        bigint id PK
        varchar title
        text description
        varchar category
        decimal reward_amount
        varchar status
        date deadline
        bigint client_id FK
        bigint assigned_dev_id FK
        timestamp created_at
        timestamp updated_at
    }

    PROPOSALS {
        bigint id PK
        bigint bounty_id FK
        bigint developer_id FK
        text approach_description
        decimal proposed_amount
        int estimated_days
        varchar status
        timestamp created_at
        timestamp updated_at
    }

    MILESTONES {
        bigint id PK
        bigint proposal_id FK
        varchar title
        text description
        varchar deliverable_url
        varchar status
        timestamp submitted_at
        timestamp approved_at
    }

    REVIEWS {
        bigint id PK
        bigint bounty_id FK
        bigint reviewer_id FK
        bigint reviewee_id FK
        int rating
        text feedback
        timestamp created_at
    }

    REFRESH_TOKENS {
        bigint id PK
        bigint user_id FK
        varchar token UK
        timestamp expiry_date
        boolean revoked
        timestamp created_at
    }
```

---

## 4. Lifecycle State Machines

### A. Bounty Lifecycle
```text
  [ DRAFT ] ──► (Client funds escrow via Stripe) ──► [ OPEN ]
                                                         │
                                                         ▼ (Developer submits proposal)
                                                    [ IN_REVIEW ]
                                                         │
                                                         ▼ (Client accepts one proposal)
                                                    [ ASSIGNED ]
                                                         │
                                                         ▼ (Developer submits deliverables)
                                                    [ IN_PROGRESS ]
                                                         │
                                                         ▼ (Client approves 100% of milestones)
                                                    [ COMPLETED ] (Triggers payout release)

  * CANCELLED: Permitted only from OPEN / IN_REVIEW prior to developer assignment.
  * DISPUTED: Triggers when either party raises a dispute on a milestone.
```

### B. Proposal Lifecycle
```text
  [ PENDING ] ──► (Client accepts) ──► [ ACCEPTED ] (Auto-rejects competing bids)
       │
       └────────► (Client rejects / Bounty cancelled) ──► [ REJECTED ]
```

### C. Milestone Lifecycle
```text
  [ PENDING ] ──► (Developer submits work) ──► [ SUBMITTED ]
                                                    │
                   ┌────────────────────────────────┴──────────────────────────────┐
                   ▼                                                               ▼
        (Client approves)                                                (Client requests revision)
                   │                                                               │
                   ▼                                                               ▼
             [ APPROVED ] ──► (Disburses milestone funds)                  [ REVISION_REQUESTED ]
                                                                                   │
                                                                                   ▼
                                                                           (Resubmit deliverable)
```

---

## 5. Commercial Escrow & Monetization Architecture

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Backend as OpenBounty Backend
    participant Stripe as Stripe Gateway & Connect
    actor Developer
    participant Ledger as Double-Entry Ledger

    Client->>Backend: Post Bounty ($1,000 reward)
    Backend->>Stripe: Create PaymentIntent / Checkout Session ($1,000)
    Client->>Stripe: Authorize & Pay
    Stripe-->>Backend: Webhook payment_intent.succeeded
    Backend->>Ledger: DEBIT: PLATFORM_CASH ($1,000) | CREDIT: CLIENT_ESCROW_LOCKED ($1,000)
    Backend->>Backend: Transition Bounty -> OPEN

    Developer->>Backend: Submit Proposal & Milestones (e.g. 2 x $500)
    Client->>Backend: Accept Proposal -> Bounty ASSIGNED

    Developer->>Backend: Submit Deliverable URL (Milestone 1)
    Client->>Backend: Approve Milestone 1 ($500)
    Backend->>Ledger: Split $500 -> $425 to Developer (85%) + $75 Platform Take-Rate (15%)
    Backend->>Stripe: Stripe Transfer $425 to Developer Connect Account
    Backend->>Developer: Funds Disbursed to Bank Account
```

---

## 6. Layered Enterprise Architecture

```text
[ Client Application (Next.js / React / Mobile) ]
                      │ HTTPS (REST + WebSockets)
                      ▼
[ Cloudflare WAF + SSL Termination + DDoS Defense ]
                      │
                      ▼
[ Spring Security 6 Filter Chain (Stateless JWT + RTR + CORS) ]
                      │
                      ▼
[ Controller Layer (@RestController) ] ── Validates Jakarta DTOs, Handles RFC 7807 responses
                      │
                      ▼
[ Service Layer (@Service) ] ──────────── State machine transitions, Concurrency locks (@Transactional)
         │                   │                         │
         ▼                   ▼                         ▼
[ Stripe / Escrow Engine ] [ S3 / R2 Object Storage ] [ Spring Events / Async Workers ]
         │                   │                         │
         ▼                   ▼                         ▼
[ Payment Webhooks ]   [ Deliverable Proofs ]     [ Email Notifications / Auto-release ]
                      │
                      ▼
[ Repository Layer (@Repository) ] ────── Spring Data JPA, Pessimistic Locking, Projections
                      │
                      ▼
[ PostgreSQL Managed DB ] ────────────── Relational tables, Flyway migrations, B-Tree indexes
```

---

## 7. REST API Endpoint Matrix

| HTTP Method | Endpoint | Access Role | Purpose |
| :--- | :--- | :--- | :--- |
| **Authentication** | | | |
| `POST` | `/api/auth/register` | Public | Register client or developer account |
| `POST` | `/api/auth/login` | Public | Authenticate user & return JWT + Refresh token |
| `POST` | `/api/auth/refresh` | Public | Rotate refresh token with reuse detection |
| `POST` | `/api/auth/logout` | Authenticated | Invalidate active refresh token session |
| `GET` | `/api/auth/me` | Authenticated | Retrieve authenticated user profile |
| **Bounties** | | | |
| `POST` | `/api/bounties` | `ROLE_CLIENT` | Create technical challenge (XSS sanitized) |
| `GET` | `/api/bounties` | Public | Search, filter, and paginate open challenges |
| `GET` | `/api/bounties/{id}` | Public | Get single bounty with full details |
| `PATCH` | `/api/bounties/{id}/cancel` | `ROLE_CLIENT` (Owner) | Cancel bounty and auto-reject pending proposals |
| **Proposals** | | | |
| `POST` | `/api/bounties/{id}/proposals` | `ROLE_DEVELOPER` | Submit technical solution proposal |
| `GET` | `/api/bounties/{id}/proposals` | `ROLE_CLIENT` (Owner) | View all proposals for owner's bounty |
| `PATCH` | `/api/proposals/{id}/accept` | `ROLE_CLIENT` (Owner) | Concurrently lock bounty and assign developer |
| `PATCH` | `/api/proposals/{id}/reject` | `ROLE_CLIENT` (Owner) | Explicitly reject a proposal |
| **Milestones** | | | |
| `POST` | `/api/milestones/{id}/submit` | `ROLE_DEVELOPER` (Assigned)| Submit deliverable proof (PR, staging URL, docs) |
| `PATCH` | `/api/milestones/{id}/approve` | `ROLE_CLIENT` (Owner) | Approve milestone & trigger payout disbursement |
| `POST` | `/api/milestones/{id}/revision` | `ROLE_CLIENT` (Owner) | Request revisions on submitted deliverable |
| `POST` | `/api/milestones/{id}/dispute` | Assigned Dev / Owner | Freeze milestone escrow and escalate to admin |
| **Reviews & Reputation** | | | |
| `POST` | `/api/reviews` | Assigned Dev / Owner | Submit 1–5 star rating and feedback post-completion |
| `GET` | `/api/users/{id}/reviews` | Public | View received reviews and reputation history |
| **Platform Analytics** | | | |
| `GET` | `/api/analytics/overview` | Public | Total bounties, funds disbursed, active solver count |
| `GET` | `/api/analytics/categories` | Public | Category distribution and volume breakdown |
