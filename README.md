# Task Tracker Backend

A REST API (+ real-time WebSocket notifications) for team task tracking and
collaboration: accounts, teams, task assignment, comments, attachments, and
AI-assisted task descriptions. Built for the Airtribe backend assignment.

## Stack

Java 21 · Spring Boot 3 (Web, Security, Data JPA, Validation, WebSocket) ·
PostgreSQL + Flyway · JWT auth · STOMP over WebSocket · springdoc-openapi ·
JUnit 5 / Mockito / Testcontainers · Maven

## Architecture

Package-by-feature modular monolith — one module per domain, each owning its
own controller, service, repository, entity, and DTOs:

```
com.airtribe.tasktracker
├── config          cross-cutting config (security, OpenAPI)
├── common          API response envelope, domain exceptions, global error handling, JPA auditing base
├── security        JWT issuing/parsing, the authenticated-user principal, the stateless filter chain
├── auth             register / login / refresh / logout
├── user             profile read/update
├── team             teams, membership, roles, invitations — the authorization chokepoint other modules call into
├── task             task CRUD, filtering/search/pagination, status transitions, assignment
├── comment          per-task comments
├── attachment       per-task file upload/download/delete behind a swappable storage interface
├── notification     persisted notifications + real-time STOMP push
└── ai               AI-assisted task description generation (Claude API, with an offline no-op fallback)
```

Every team-scoped endpoint (tasks, comments, attachments, invitations) checks
membership through `team.TeamMembershipService` before acting. Full design
rationale in
[`docs/superpowers/specs/2026-08-30-task-tracker-backend-design.md`](docs/superpowers/specs/2026-08-30-task-tracker-backend-design.md).

## Getting Started

**Prerequisites:** JDK 21, Maven, Docker (for local Postgres and for the
Testcontainers-backed tests).

```bash
git clone <this-repo-url>
cd task-tracker-backend
cp .env.example .env        # then edit values as needed
docker compose up -d postgres
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. Interactive docs:
`http://localhost:8080/swagger-ui.html`. Raw OpenAPI document:
`http://localhost:8080/v3/api-docs`.

### Configuration

All configuration is environment-variable driven (see `.env.example` and
`src/main/resources/application.yml`):

| Variable | Purpose |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Signing key for access/refresh tokens — set a long random value outside local dev |
| `STORAGE_ROOT` | Local directory attachments are written to |
| `ANTHROPIC_API_KEY` | Optional — enables real AI-generated task descriptions; omitted/blank falls back to a clear "unavailable" message instead of failing |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of origins allowed to call the API from a browser |

### Running Tests

```bash
mvn test      # unit + integration + e2e tests (spins up Testcontainers Postgres automatically)
mvn verify    # the above, plus the JaCoCo 80% coverage gate
```

Coverage report after `mvn verify`: `target/site/jacoco/index.html`.

## API Overview

Every response is wrapped as `{ "success": bool, "data": ..., "error": {code, message} | null, "meta": {page, limit, total} | null }`
(the single exception is attachment download, which returns raw file bytes).
Full endpoint-by-endpoint reference: Swagger UI at `/swagger-ui.html`.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/{register,login,refresh,logout}` |
| Profile | `GET/PUT /api/users/me` |
| Teams | `POST /api/teams`, `GET /api/teams`, `GET /api/teams/{id}`, `GET /api/teams/{id}/members` |
| Invitations | `POST /api/teams/{id}/invitations`, `POST /api/invitations/{token}/accept` |
| Tasks | `POST/GET /api/teams/{id}/tasks`, `GET/PUT/DELETE /api/tasks/{id}`, `PATCH /api/tasks/{id}/status`, `PATCH /api/tasks/{id}/assign`, `GET /api/tasks/mine` |
| Comments | `POST/GET /api/tasks/{id}/comments` |
| Attachments | `POST /api/tasks/{id}/attachments`, `GET/DELETE /api/tasks/{id}/attachments/{attachmentId}` |
| AI | `POST /api/tasks/ai/generate-description` |
| Notifications | `GET /api/notifications`, `PATCH /api/notifications/{id}/read`, `WS /ws` (STOMP, subscribe to `/user/queue/notifications`, connect with `?token=<accessToken>`) |

## Project Docs

- [Design spec](docs/superpowers/specs/2026-08-30-task-tracker-backend-design.md) — requirements, architecture rationale, data model, security model
- [Implementation plan](docs/superpowers/plans/2026-08-30-task-tracker-backend.md) — the task-by-task build plan this project was implemented from
