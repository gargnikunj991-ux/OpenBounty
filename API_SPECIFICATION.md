# OpenBounty — Complete RESTful API Specification (v1.0)

This document provides the exhaustive API reference and contract for the **OpenBounty** platform, following standard RESTful design conventions and RFC 7807 Problem Details for HTTP APIs.

---

## Base Configuration

* **Base URL:** `http://localhost:8080/api` (Development) / `https://api.openbounty.dev/api` (Production)
* **Authentication:** Bearer Token via `Authorization: Bearer <jwt_token>` header.
* **Content-Type:** `application/json` (All request bodies and responses unless otherwise specified).
* **Date-Time Format:** ISO-8601 (`YYYY-MM-DDTHH:mm:ss.sssZ` or `YYYY-MM-DD`).

---

## 1. Authentication & User Profile Module (`/api/auth`)

### 1.1 Register New Account
* **Endpoint:** `POST /api/auth/register`
* **Access:** Public
* **Description:** Creates a new client or developer user account with hashed password storage.

#### Request Body
```json
{
  "name": "Alex Johnson",
  "email": "alex.johnson@example.com",
  "password": "SecurePassword123!",
  "role": "ROLE_DEVELOPER"
}
```
* **Validation Rules:**
  - `name`: `@NotBlank`, `@Size(min = 2, max = 100)`
  - `email`: `@NotBlank`, `@Email`, unique in system
  - `password`: `@NotBlank`, `@Size(min = 8, max = 64)`
  - `role`: `@NotNull`, must be `ROLE_CLIENT` or `ROLE_DEVELOPER`

#### Response `201 Created`
```json
{
  "id": 1,
  "name": "Alex Johnson",
  "email": "alex.johnson@example.com",
  "role": "ROLE_DEVELOPER",
  "reputationScore": 0,
  "createdAt": "2026-08-31T20:45:00.000Z"
}
```

---

### 1.2 User Login & Token Generation
* **Endpoint:** `POST /api/auth/login`
* **Access:** Public
* **Description:** Verifies credentials and returns a signed JWT access token.

#### Request Body
```json
{
  "email": "alex.johnson@example.com",
  "password": "SecurePassword123!"
}
```

#### Response `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "expiresInMs": 86400000,
  "user": {
    "id": 1,
    "name": "Alex Johnson",
    "email": "alex.johnson@example.com",
    "role": "ROLE_DEVELOPER",
    "reputationScore": 0
  }
}
```

---

### 1.3 Get Current Authenticated Profile
* **Endpoint:** `GET /api/auth/me`
* **Access:** Authenticated (`ROLE_CLIENT`, `ROLE_DEVELOPER`, `ROLE_ADMIN`)
* **Headers:** `Authorization: Bearer <jwt_token>`

#### Response `200 OK`
```json
{
  "id": 1,
  "name": "Alex Johnson",
  "email": "alex.johnson@example.com",
  "role": "ROLE_DEVELOPER",
  "reputationScore": 25,
  "createdAt": "2026-08-31T20:45:00.000Z",
  "updatedAt": "2026-08-31T21:00:00.000Z"
}
```

---

## 2. Bounty / Challenge Management Module (`/api/bounties`)

### 2.1 Create a Bounty
* **Endpoint:** `POST /api/bounties`
* **Access:** `ROLE_CLIENT`
* **Headers:** `Authorization: Bearer <jwt_token>`

#### Request Body
```json
{
  "title": "Build Spring Security 6 JWT Stateless Auth Engine",
  "description": "Implement a full stateless JWT authentication engine with role-based access control, refresh tokens, and swagger integration.",
  "category": "BACKEND_API",
  "rewardAmount": 1500.00,
  "deadline": "2026-09-30"
}
```

#### Response `201 Created`
```json
{
  "id": 101,
  "title": "Build Spring Security 6 JWT Stateless Auth Engine",
  "description": "Implement a full stateless JWT authentication engine with role-based access control, refresh tokens, and swagger integration.",
  "category": "BACKEND_API",
  "rewardAmount": 1500.00,
  "status": "OPEN",
  "deadline": "2026-09-30",
  "client": {
    "id": 5,
    "name": "Acme Corp",
    "email": "lead@acmecorp.io"
  },
  "assignedDeveloper": null,
  "createdAt": "2026-08-31T21:10:00.000Z"
}
```

---

### 2.2 List & Search Bounties (Paginated & Filtered)
* **Endpoint:** `GET /api/bounties`
* **Access:** Public
* **Query Parameters:**
  - `status` *(optional)*: `OPEN`, `IN_REVIEW`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`
  - `category` *(optional)*: `WEB_DEVELOPMENT`, `AI_ML`, `BACKEND_API`, etc.
  - `search` *(optional)*: Keyword matching title or description.
  - `page` *(optional, default `0`)*: Zero-indexed page number.
  - `size` *(optional, default `10`)*: Items per page.
  - `sort` *(optional, default `createdAt,desc`)*: Sort field and direction.

#### Example Request
`GET /api/bounties?category=BACKEND_API&status=OPEN&page=0&size=10&sort=rewardAmount,desc`

#### Response `200 OK`
```json
{
  "content": [
    {
      "id": 101,
      "title": "Build Spring Security 6 JWT Stateless Auth Engine",
      "category": "BACKEND_API",
      "rewardAmount": 1500.00,
      "status": "OPEN",
      "deadline": "2026-09-30",
      "client": {
        "id": 5,
        "name": "Acme Corp"
      },
      "createdAt": "2026-08-31T21:10:00.000Z"
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

---

### 2.3 Get Bounty Details by ID
* **Endpoint:** `GET /api/bounties/{id}`
* **Access:** Public

#### Response `200 OK`
```json
{
  "id": 101,
  "title": "Build Spring Security 6 JWT Stateless Auth Engine",
  "description": "Implement a full stateless JWT authentication engine...",
  "category": "BACKEND_API",
  "rewardAmount": 1500.00,
  "status": "OPEN",
  "deadline": "2026-09-30",
  "client": {
    "id": 5,
    "name": "Acme Corp",
    "email": "lead@acmecorp.io",
    "reputationScore": 100
  },
  "assignedDeveloper": null,
  "createdAt": "2026-08-31T21:10:00.000Z",
  "updatedAt": "2026-08-31T21:10:00.000Z"
}
```

---

### 2.4 Cancel Bounty
* **Endpoint:** `PATCH /api/bounties/{id}/cancel`
* **Access:** `ROLE_CLIENT` (Must be the creator/owner)
* **Guard Conditions:** Cannot cancel if bounty is already `ASSIGNED`, `IN_PROGRESS`, or `COMPLETED`.

#### Response `200 OK`
```json
{
  "id": 101,
  "status": "CANCELLED",
  "message": "Bounty has been successfully cancelled."
}
```

---

## 3. Proposal & Bidding Module (`/api/bounties/{id}/proposals` & `/api/proposals`)

### 3.1 Submit a Proposal
* **Endpoint:** `POST /api/bounties/{id}/proposals`
* **Access:** `ROLE_DEVELOPER`
* **Headers:** `Authorization: Bearer <jwt_token>`
* **Guard Conditions:** Developer cannot submit duplicate proposals for the same bounty; bounty must be in `OPEN` or `IN_REVIEW` status.

#### Request Body
```json
{
  "approachDescription": "I will implement a modular filter chain using JJWT library, with Redis blacklist support and JUnit integration tests.",
  "proposedAmount": 1400.00,
  "estimatedDays": 7,
  "milestones": [
    {
      "title": "Milestone 1: Filter Chain & JWT Token Generation",
      "description": "Setup SecurityFilterChain and JwtService with RSA-256 / HMAC-SHA256 signature verification."
    },
    {
      "title": "Milestone 2: Role Authorization & Integration Tests",
      "description": "Add @PreAuthorize test cases and MockMvc test coverage."
    }
  ]
}
```

#### Response `201 Created`
```json
{
  "id": 201,
  "bountyId": 101,
  "developer": {
    "id": 1,
    "name": "Alex Johnson",
    "reputationScore": 25
  },
  "approachDescription": "I will implement a modular filter chain...",
  "proposedAmount": 1400.00,
  "estimatedDays": 7,
  "status": "PENDING",
  "milestones": [
    {
      "id": 301,
      "title": "Milestone 1: Filter Chain & JWT Token Generation",
      "status": "PENDING"
    },
    {
      "id": 302,
      "title": "Milestone 2: Role Authorization & Integration Tests",
      "status": "PENDING"
    }
  ],
  "createdAt": "2026-08-31T21:15:00.000Z"
}
```

---

### 3.2 List Proposals for a Bounty
* **Endpoint:** `GET /api/bounties/{id}/proposals`
* **Access:** `ROLE_CLIENT` (Owner of the bounty)
* **Response `200 OK`:** Array of proposals with developer profiles and milestone breakdowns.

---

### 3.3 Accept Proposal (Atomic Transaction)
* **Endpoint:** `PATCH /api/proposals/{id}/accept`
* **Access:** `ROLE_CLIENT` (Owner of the bounty)
* **Atomic Side Effects:**
  1. Sets proposal status to `ACCEPTED`.
  2. Sets all competing proposals for this bounty to `REJECTED`.
  3. Updates `bounties.assigned_dev_id` to winning developer.
  4. Transitions `bounties.status` to `ASSIGNED`.

#### Response `200 OK`
```json
{
  "proposalId": 201,
  "bountyId": 101,
  "status": "ACCEPTED",
  "assignedDeveloperId": 1,
  "bountyStatus": "ASSIGNED",
  "message": "Proposal accepted successfully. Winning developer assigned."
}
```

---

### 3.4 Reject Proposal
* **Endpoint:** `PATCH /api/proposals/{id}/reject`
* **Access:** `ROLE_CLIENT` (Owner of the bounty)

#### Response `200 OK`
```json
{
  "proposalId": 201,
  "status": "REJECTED"
}
```

---

## 4. Milestone Deliverables Module (`/api/milestones`)

### 4.1 Submit Deliverable Proof
* **Endpoint:** `POST /api/milestones/{id}/submit`
* **Access:** `ROLE_DEVELOPER` (Assigned developer only)

#### Request Body
```json
{
  "deliverableUrl": "https://github.com/alex-dev/openbounty-security-module/pull/1",
  "notes": "All unit and integration tests passing. Swagger endpoint tested."
}
```

#### Response `200 OK`
```json
{
  "id": 301,
  "title": "Milestone 1: Filter Chain & JWT Token Generation",
  "deliverableUrl": "https://github.com/alex-dev/openbounty-security-module/pull/1",
  "status": "SUBMITTED",
  "submittedAt": "2026-08-31T21:20:00.000Z"
}
```

---

### 4.2 Approve Milestone
* **Endpoint:** `PATCH /api/milestones/{id}/approve`
* **Access:** `ROLE_CLIENT` (Owner of the bounty)
* **Side Effects:**
  - Sets milestone status to `APPROVED` and records `approved_at`.
  - **Auto-Completion Trigger:** If 100% of milestones for the associated proposal are `APPROVED`, transitions bounty status to `COMPLETED` and calculates developer reputation points.

#### Response `200 OK`
```json
{
  "id": 301,
  "status": "APPROVED",
  "approvedAt": "2026-08-31T21:25:00.000Z",
  "allMilestonesApproved": true,
  "bountyStatus": "COMPLETED",
  "message": "Milestone approved. All deliverables verified; bounty marked as COMPLETED."
}
```

---

## 5. Reviews & Reputation Module (`/api/reviews`)

### 5.1 Submit a Review
* **Endpoint:** `POST /api/reviews`
* **Access:** Authenticated (Client or Developer participant of a `COMPLETED` bounty)

#### Request Body
```json
{
  "bountyId": 101,
  "revieweeId": 1,
  "rating": 5,
  "feedback": "Outstanding code quality, thorough tests, and delivered 2 days ahead of schedule!"
}
```

#### Response `201 Created`
```json
{
  "id": 401,
  "bountyId": 101,
  "reviewerId": 5,
  "revieweeId": 1,
  "rating": 5,
  "feedback": "Outstanding code quality...",
  "createdAt": "2026-08-31T21:30:00.000Z"
}
```

---

## 6. Platform Analytics Module (`/api/analytics`)

### 6.1 Platform Overview Metrics
* **Endpoint:** `GET /api/analytics/overview`
* **Access:** Public

#### Response `200 OK`
```json
{
  "totalBounties": 250,
  "activeBounties": 84,
  "completedBounties": 152,
  "totalFundsDisbursed": 385000.00,
  "totalDevelopers": 1200,
  "totalClients": 340
}
```

---

### 6.2 Category Distribution Breakdown
* **Endpoint:** `GET /api/analytics/categories`
* **Access:** Public

#### Response `200 OK`
```json
[
  { "category": "BACKEND_API", "bountyCount": 95, "totalRewardAmount": 142500.00 },
  { "category": "AI_ML", "bountyCount": 60, "totalRewardAmount": 120000.00 },
  { "category": "WEB_DEVELOPMENT", "bountyCount": 50, "totalRewardAmount": 65000.00 },
  { "category": "SECURITY_AUDIT", "bountyCount": 25, "totalRewardAmount": 37500.00 },
  { "category": "DEVOPS_CLOUD", "bountyCount": 20, "totalRewardAmount": 20000.00 }
]
```

---

## 7. Standardized Error Handling (RFC 7807 Problem Details)

All error responses return a standardized JSON structure:

```json
{
  "type": "https://api.openbounty.dev/errors/resource-not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Bounty with ID 999 does not exist.",
  "instance": "/api/bounties/999",
  "timestamp": "2026-08-31T21:35:00.000Z"
}
```

### Validation Failure Format (`400 Bad Request`)
```json
{
  "type": "https://api.openbounty.dev/errors/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "One or more request fields failed validation.",
  "instance": "/api/bounties",
  "timestamp": "2026-08-31T21:35:00.000Z",
  "errors": {
    "rewardAmount": "Reward amount must be greater than zero.",
    "deadline": "Deadline must be a future date."
  }
}
```

### Common HTTP Status Codes
* `200 OK`: Request succeeded.
* `201 Created`: Resource successfully created.
* `400 Bad Request`: Malformed payload or validation failure.
* `401 Unauthorized`: Missing or invalid JWT Bearer token.
* `403 Forbidden`: Authenticated user lacks required role or ownership.
* `404 Not Found`: Requested entity does not exist.
* `409 Conflict`: Business state conflict (e.g. Duplicate proposal or illegal state transition).
* `500 Internal Server Error`: Unhandled server exception.
