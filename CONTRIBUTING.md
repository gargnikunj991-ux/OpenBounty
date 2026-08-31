# Contributing to OpenBounty

Thank you for your interest in contributing to **OpenBounty**! This document provides guidelines and standard workflows to ensure smooth collaboration.

---

## 1. Development Setup

### Prerequisites
* **Java Development Kit (JDK) 21 LTS**
* **Apache Maven 3.9+**
* **Docker Desktop & Docker Compose** (for local PostgreSQL database)
* **Git**

### Initial Setup
```bash
# 1. Clone your fork
git clone https://github.com/gargnikunj991-ux/OpenBounty.git
cd OpenBounty

# 2. Start PostgreSQL container
docker compose up -d postgres

# 3. Create .env configuration
cp .env.example .env

# 4. Build and run tests
mvn clean test
```

---

## 2. Git Workflow & Branch Naming

We follow the feature-branch workflow. Always branch off `main`.

### Branch Naming Conventions
* `feat/<feature-name>`: New feature or enhancement (e.g. `feat/proposal-bidding-api`).
* `fix/<bug-description>`: Bug fix (e.g. `fix/jwt-expiration-timezone`).
* `refactor/<module>`: Code restructuring without feature change (e.g. `refactor/bounty-repository`).
* `docs/<topic>`: Documentation updates (e.g. `docs/api-spec-updates`).
* `test/<scope>`: Adding or improving test suites (e.g. `test/auth-integration-tests`).

---

## 3. Commit Message Standards (Conventional Commits)

Format: `<type>(<scope>): <short summary>`

### Examples:
* `feat(bounty): implement multi-criteria filtering and pagination in BountyRepository`
* `fix(auth): handle expired JWT token with RFC 7807 problem details response`
* `test(proposal): add MockMvc integration tests for proposal acceptance`
* `docs(readme): update quickstart and swagger links`

---

## 4. Coding & Architectural Guidelines

1. **Domain-Driven Layering**:
   - `model`: JPA entities only. Use `@Table(name = "...")` and `@Column(name = "...")`.
   - `repository`: Spring Data JPA interfaces.
   - `service`: Business logic, transactional boundaries (`@Transactional`), and authorization guards.
   - `controller`: `@RestController` validating incoming `@Valid` DTOs and mapping status codes.
   - `dto`: Request and response data transfer objects. Entities must NEVER be returned directly from controller endpoints.
2. **Entity Design Rules**:
   - Always specify `FetchType.LAZY` on `@ManyToOne` and `@OneToMany` relationships.
   - Exclude sensitive and relational fields from Lombok `@ToString(exclude = {"password", ...})`.
   - Store enums as strings with `@Enumerated(EnumType.STRING)`.
3. **Validation**:
   - Apply Jakarta Bean Validation annotations (`@NotBlank`, `@NotNull`, `@Size`, `@Min`, `@Email`) on all request DTOs.
4. **Testing**:
   - All new business logic must include unit tests using JUnit 5 and Mockito.
   - All REST controllers must include integration tests using MockMvc.

---

## 5. Pull Request Checklist

Before submitting a Pull Request, ensure:
- [ ] Code compiles without warnings: `mvn clean compile`
- [ ] All unit and integration tests pass: `mvn test`
- [ ] Clean Git commit history following conventional commits.
- [ ] Updated corresponding documentation in `API_SPECIFICATION.md` or `SYSTEM_DESIGN.md` if APIs or schemas changed.
