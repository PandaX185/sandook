# AGENTS.md — sandook

## Hat to wear
Cash box ledger (صندوق) — parking, petty cash, shop cash & deposits, transfers. Think **full-stack engineer** — Spring Boot API + Next.js UI, one monorepo, Docker Compose as the dev environment. Money math lives in SQL — keep balances SQL-computed, never hand-rolled in app code.

## Stack
- **Backend:** Java 17 · Spring Boot 4.1 · Spring Security 7 (JWT) · Spring Data JPA
- **Database:** PostgreSQL 17 · Flyway migrations (`ddl-auto=validate` — schema must match entities)
- **Frontend:** Next.js 16 · React 19 · Tailwind CSS 4 · TypeScript
- **Infra:** Docker Compose (db + backend + frontend)
- **Tests:** JUnit 5 · Testcontainers · MockMvc

## Commands (root Makefile is canonical)
```bash
make setup            # first-time: .env, npm install, build + start stack
make up / down / restart / stop / ps / build
make logs / logs-backend / logs-frontend / logs-db
make db-shell / backend-shell / frontend-shell
make db-reset         # DESTRUCTIVE — wipes DB volume, requires typing "reset"
make backend-test     # Testcontainers (needs Docker running)
make backend-package / backend-run
make frontend-install / frontend-dev / frontend-build / frontend-lint
```

## Conventions
- **Ports:** frontend `:3000`, backend `:8081`, host db `:5433`.
- **Auth:** JWT with refresh rotation + reuse detection; roles `EDITOR` (full) vs `VIEWER` (read-only); bootstrap admin auto-created on empty DB. Don't weaken this — it's the security backbone.
- **Schema:** Flyway migrations only (never let JPA auto-DDL in prod); entities validated against migrations at startup.
- **Errors:** Problem Details responses, actuator health/readiness, CORS-ready.
- **Ledger rules:** balances are computed in SQL (top-up ↔ cash day withdraw linkage, deposit sanity checks). Preserve that invariant.
- **Plans:** `plan/` with `feat-*` / `brain-*` docs — read the relevant one before touching a module.
- **Domain packages:** `backend/src/main/java/com/sandook/ledger/{auth,common,security,user,...}` — keep module boundaries.
- Handles real money — be conservative with destructive operations; `db-reset` needs the confirm prompt for a reason.

## Workflows
Follow the project workflows (`/feat`, `/bug`, `/refactor`, `/chore`, `/brain`). **No coding without explicit approval.**

## Status
Active — phase 2 shipped (parking, transfers, audit, users pages). Next: watch the `plan/` docs for what's queued.
