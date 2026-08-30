# Task Tracker & Team Collaboration Backend — Design Spec

**Date:** 2026-08-30
**Status:** Approved for implementation

## 1. Purpose

A backend system for a task tracking and management application that lets teams
create, assign, and track tasks, and collaborate via comments and attachments.
Delivered as a REST API (+ one WebSocket endpoint) — no frontend in scope.

This fulfills the Airtribe "Task Tracker" assignment brief, including both
optional extensions: real-time notifications and AI-assisted task description
generation.

## 2. Stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3 (Web, Security, Data JPA, Validation, WebSocket) |
| Build tool | Maven |
| Database | PostgreSQL |
| Migrations | Flyway |
| Auth | Spring Security + JWT (access + refresh tokens), BCrypt password hashing |
| Real-time | Spring WebSocket with STOMP, per-user notification queue |
| AI | Anthropic Claude Messages API via a pluggable `AiService` interface |
| API docs | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5, Mockito, Testcontainers (Postgres), MockMvc/RestAssured for full-flow tests, JaCoCo coverage gate |

## 3. Architecture

**Package-by-feature modular monolith.** One module per domain, each owning its
own controller, service, repository, entity, and DTOs:

```
com.airtribe.tasktracker
├── config          (SecurityConfig, WebSocketConfig, OpenApiConfig, CorsConfig)
├── common           (ApiResponse envelope, PageMeta, GlobalExceptionHandler,
│                      domain exceptions, base auditing fields)
├── auth              (register/login/refresh/logout, JwtService, RefreshTokenStore)
├── user              (profile read/update)
├── team              (Team, TeamMembership, Invitation, roles, membership checks)
├── task              (Task CRUD, status transitions, assignment, filtering/search)
├── comment           (Comment CRUD, scoped to a task)
├── attachment        (Attachment upload/download/delete, StorageService abstraction)
├── notification      (Notification persistence + STOMP push)
└── ai                (AiService interface, ClaudeAiService impl, no-op fallback)
```

**Why this over alternatives:**
- *Layer-first* (`controllers/`, `services/`, `repositories/`) was considered but
  rejected — with 8 domains in play, layer-first folders balloon into large,
  low-cohesion files. Package-by-feature keeps each file small and each
  feature independently understandable, per the project's file-organization
  standard (many small, high-cohesion files over few large ones).
- *Hexagonal / ports-and-adapters* was considered but rejected as more
  decoupling machinery than an assignment-scoped monolith needs — the
  `StorageService` and `AiService` interfaces already give the two genuinely
  swappable integration points their own seam, without applying ports/adapters
  everywhere.

Each module communicates with others only through its service's public
interface (e.g. `task` calls `team.TeamMembershipService.requireMember(...)`
rather than querying team tables directly), so modules stay independently
testable and understandable without reading each other's internals.

## 4. Data Model

All tables have `id` (UUID), `created_at`, `updated_at` unless noted.

- **User** — `name`, `email` (unique), `password_hash`, `avatar_url` (nullable)
- **Team** — `name`, `description`, `owner_id` (FK User)
- **TeamMembership** — `team_id`, `user_id`, `role` (`OWNER`/`ADMIN`/`MEMBER`), `joined_at`
  — unique on (`team_id`, `user_id`)
- **Invitation** — `team_id`, `email`, `token` (unique), `status`
  (`PENDING`/`ACCEPTED`/`EXPIRED`), `invited_by` (FK User), `expires_at`
- **Task** — `team_id` (FK Team, required), `title`, `description`, `status`
  (`OPEN`/`IN_PROGRESS`/`COMPLETED`), `priority` (`LOW`/`MEDIUM`/`HIGH`, default
  `MEDIUM`), `due_date` (nullable), `created_by` (FK User), `assignee_id`
  (FK User, nullable)
- **Comment** — `task_id` (FK Task), `author_id` (FK User), `body`
- **Attachment** — `task_id` (FK Task), `uploaded_by` (FK User), `filename`,
  `storage_path`, `content_type`, `size_bytes`
- **Notification** — `user_id` (FK User, recipient), `type`
  (`TASK_ASSIGNED`/`TASK_UPDATED`/`COMMENT_ADDED`), `payload` (JSONB), `read`
  (boolean, default false)

Every task belongs to exactly one team — the brief frames all task activity
around team collaboration, so unteamed/personal tasks are out of scope.

Indexes: `task(team_id, status)`, `task(assignee_id)`, `comment(task_id)`,
`notification(user_id, read)`, full-text/`ILIKE` support on `task(title,
description)` for search.

## 5. API Surface

All responses use a single envelope: `{ success, data, error, meta }`, where
`meta` carries pagination (`page`, `limit`, `total`) on list endpoints.

**Auth**
- `POST /api/auth/register` — create account
- `POST /api/auth/login` — returns access + refresh token
- `POST /api/auth/refresh` — rotate refresh token, issue new access token
- `POST /api/auth/logout` — revoke refresh token

**Users**
- `GET /api/users/me`
- `PUT /api/users/me`

**Teams**
- `POST /api/teams` — create team (creator becomes `OWNER`)
- `GET /api/teams` — teams the current user belongs to
- `GET /api/teams/{teamId}`
- `GET /api/teams/{teamId}/members`
- `POST /api/teams/{teamId}/invitations` — `OWNER`/`ADMIN` only
- `POST /api/invitations/{token}/accept`

**Tasks**
- `POST /api/teams/{teamId}/tasks`
- `GET /api/teams/{teamId}/tasks?status=&assignee=&q=&sort=&page=&limit=`
- `GET /api/tasks/{taskId}`
- `PUT /api/tasks/{taskId}`
- `PATCH /api/tasks/{taskId}/status`
- `PATCH /api/tasks/{taskId}/assign`
- `DELETE /api/tasks/{taskId}`
- `GET /api/tasks/mine?status=&q=&page=&limit=` — tasks assigned to the current user, across teams

**Comments**
- `POST /api/tasks/{taskId}/comments`
- `GET /api/tasks/{taskId}/comments?page=&limit=`

**Attachments**
- `POST /api/tasks/{taskId}/attachments` — multipart upload
- `GET /api/tasks/{taskId}/attachments/{attachmentId}` — download
- `DELETE /api/tasks/{taskId}/attachments/{attachmentId}`

**AI**
- `POST /api/tasks/ai/generate-description` — `{ title, notes }` → drafted description

**Notifications**
- `GET /api/notifications?page=&limit=`
- `PATCH /api/notifications/{id}/read`
- `WS /ws/notifications` — STOMP, client subscribes to `/user/queue/notifications`

## 6. Security & Authorization

- Passwords hashed with BCrypt; never logged or returned in responses.
- JWT access tokens (short-lived, ~15 min) + refresh tokens (longer-lived,
  rotated on every refresh, stored hashed server-side so they can be revoked
  on logout).
- Every team-scoped endpoint enforces membership via a shared
  `TeamMembershipService.requireMember(teamId, userId)` check; team
  management actions additionally require `OWNER`/`ADMIN` role.
- Task edits allowed for: the task creator, the current assignee, or a team
  `ADMIN`/`OWNER`. Any team member can comment or attach files.
- All request bodies validated with Jakarta Bean Validation at the DTO
  boundary; a global `@ControllerAdvice` maps validation and domain
  exceptions to the standard error envelope, never leaking stack traces or
  internal details.
- File uploads: size cap (configurable, default 10 MB), content-type
  allowlist, filenames sanitized, files stored under an app-owned root
  directory behind a `StorageService` interface (local-disk implementation
  for this project; swappable for S3 later without touching callers).
- Secrets (`DB_*`, `JWT_SECRET`, `ANTHROPIC_API_KEY`) read from environment
  variables only, validated present at startup — never hardcoded.
- CORS restricted to configured allowed origins.

## 7. Real-Time Notifications

Spring WebSocket + STOMP. On task assignment, task update, or new comment,
the relevant service publishes a `Notification` (persisted to the DB) and
pushes it to the recipient's `/user/queue/notifications` STOMP destination if
they're connected. `GET /api/notifications` gives clients a way to catch up
on anything missed while disconnected.

## 8. AI-Assisted Task Descriptions

An `AiService` interface with:
- `ClaudeAiService` — calls the Anthropic Messages API (model configurable,
  key via `ANTHROPIC_API_KEY`) to turn a task title + short notes into a
  drafted description.
- `NoOpAiService` — active when no API key is configured, returns a clear
  "AI generation unavailable" response instead of failing; keeps local dev
  and CI runnable without network calls or a key. Unit/integration tests
  always use this implementation (or a Mockito mock) — no real API calls in
  the test suite.

## 9. Testing Strategy (target: 80%+ coverage, JaCoCo-enforced)

- **Unit** — JUnit 5 + Mockito over service-layer business rules: role
  checks, task status transitions, invitation accept flow, AI fallback
  behavior.
- **Integration** — Testcontainers-backed PostgreSQL exercising real
  repositories/JPA mappings and Flyway migrations.
- **End-to-end** — this is an API-only backend with no UI to drive, so "E2E"
  means full-flow tests via MockMvc/RestAssured against the real Spring
  context + Testcontainers DB, walking complete user journeys, e.g.:
  register → create team → invite teammate → accept invitation → create task
  → assign task → comment → attach file → recipient sees notification.

## 10. Repo & Docs

New standalone repo at `D:\Airtribe\task-tracker-backend`, pushed to GitHub as
a **public** repository. Deliverables: this spec, an implementation plan,
source, tests, and a README covering setup/run/test instructions, an
architecture diagram, and an API reference (or link to the generated Swagger
UI).

## 11. Explicitly Out of Scope

- Frontend/UI of any kind.
- Multi-tenant billing, org-level hierarchy above teams.
- Cloud file storage (S3) — abstracted for later, not implemented now.
- Push notifications to mobile/email — WebSocket + in-app history only.
- Fine-grained per-field permissions beyond the role model in §6.
