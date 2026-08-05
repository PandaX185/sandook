# Sandook (صندوق)

Cash ledger for **AL-EMAD AL SHAER** (shop) and **EMAD AL SHAER RENTAL PARKING L.L.C.**
Two books, one system — parking, petty cash, shop cash & deposits, and transfers.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 17 · Spring Boot 4.1 · Spring Security 7 (JWT) · Spring Data JPA |
| Database | PostgreSQL 17 · Flyway migrations |
| Frontend | Next.js 16 · React 19 · Tailwind CSS 4 · TypeScript |
| Infra | Docker Compose (db + backend + frontend) |
| Tests | JUnit 5 · Testcontainers · MockMvc |

## Features

- **JWT authentication** — login, refresh (rotation + reuse detection), logout
- **Role-based access** — `EDITOR` (full access) vs `VIEWER` (read-only)
- **Bootstrap admin** — first editor created automatically on an empty database
- **User API** — `/me` and user listing (editor-only)
- **Flyway-managed schema** — validated against JPA entities at startup (`ddl-auto=validate`)
- **Problem Details** error responses, actuator health/readiness endpoints, CORS-ready

## Project structure

```
sandook/
├── backend/                  # Spring Boot API
│   ├── src/main/java/com/sandook/ledger/
│   │   ├── auth/             # login/refresh/logout, JWT issuing
│   │   ├── common/           # exception handling, bootstrap admin
│   │   ├── security/         # resource server config, role extraction
│   │   └── user/             # user entity, service, controller
│   ├── src/main/resources/db/migration/   # Flyway SQL
│   └── src/test/             # integration tests (Testcontainers)
├── frontend/                 # Next.js app
├── docker-compose.yml        # db + backend + frontend
├── .env.example              # env var reference
└── plan/                     # design docs
```

## Quick start — Docker (recommended)

```bash
cp .env.example .env   # optional — dev defaults work out of the box
docker compose up -d --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8081 |
| Health check | http://localhost:8081/actuator/health |

**First login:** `POST /api/v1/auth/login` with `admin` / `admin123` (defaults — change via `.env`).

## Local development

```bash
docker compose up -d db            # Postgres only (host port 5433)

cd backend
./mvnw spring-boot:run             # API on :8081

cd ../frontend
npm install
npm run dev                        # dev server on :3000
```

> Host port 5433 is intentional — 5432 is used by other projects on this machine.

## Configuration

All settings flow through environment variables; dev defaults are defined in
`backend/src/main/resources/application.properties` and `docker-compose.yml`.

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_PASSWORD` | `sandook-dev-password` | DB password (db + backend) |
| `ADMIN_USERNAME` | `admin` | Bootstrap admin username |
| `ADMIN_PASSWORD` | *(unset — admin skipped)* | Bootstrap admin password |
| `JWT_SECRET` | dev value | HS256 signing key — **≥ 32 chars, change in production** |
| `SERVER_PORT` | `8081` | Backend port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Allowed browser origins |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8081` | API base URL used by the frontend |

Notes:

- The bootstrap admin is created **only when the `users` table is empty** — changing
  `ADMIN_PASSWORD` later does not update an existing admin.
- `JWT_SECRET` is embedded as a dev default for convenience — **never run production
  with it**; always override via environment.

## API overview

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | public | Exchange credentials for tokens |
| `POST` | `/api/v1/auth/refresh` | public (valid refresh token) | Rotate refresh token, get new pair |
| `POST` | `/api/v1/auth/logout` | authenticated | Revoke the refresh token |
| `GET` | `/api/v1/users/me` | authenticated | Current user profile |
| `GET` | `/api/v1/users` | `EDITOR` | List all users |

**Auth flow:** short-lived access token (15 min, HS256) + rotating refresh token
(30 days). Each refresh issues a new pair and revokes the old token; reusing a
revoked token is rejected (rotation + reuse detection).

## Testing

```bash
cd backend && ./mvnw test
```

Integration tests boot the full application against a disposable PostgreSQL
(Testcontainers) and cover: login, wrong-password rejection, refresh rotation,
role enforcement, and anonymous access.

## Database migrations

Flyway is **forward-only**: versioned SQL lives in
`backend/src/main/resources/db/migration/` (`V1__init.sql`, …) and is applied in
order at startup. Never edit an applied migration — add a new `V2__…` file instead.
Schema rollbacks are done via compensating migrations or database restore.

## Roadmap

See `plan/brain-cash-ledger.md` and `plan/cash-ledger-client-overview.md` for the
full design — ledger books (parking / shop), entries, deposits, transfers, and
reporting are the next milestones.
