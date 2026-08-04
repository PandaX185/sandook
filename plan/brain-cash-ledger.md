# Brain — Cash Ledger (replaces 3 Excel files)

**Date:** 2026-08-05 · **Status:** Design (no code yet)
**Owner:** AL-EMAD AL SHAER (shop) + EMAD AL SHAER RENTAL PARKING L.L.C (parking)

## Locked decisions (from Abdullah)
- **Repo name: `sandook`** (صندوق — "cash box") — location `/data/Projects/sandook`
- **Frontend: Next.js** (responsive, phone/desktop)
- **Build tool: Maven**
- **Start fresh — no Excel history seed** (seed import can come later if needed)
- **Currency:** AED now, multi-currency capable later → every money amount stored as **BIGINT minor units** (fils) + `currency_code` column, never floats.
- **Books:** two separate ledgers in one system — Shop book + Parking book — with transfer transactions between them.
- **Stack:** Java **Spring Boot 3** backend (new stack for Abdullah — Go/Node dev), Postgres, web frontend (Next.js proposed), responsive for phone/desktop. Tauri wrapper possible later.
- **Roles:** EDITOR (full CRUD + manage users) / VIEWER (read-only reports). Login = username + password (argon2/bcrypt), JWT sessions.
- **Audit log** on every write (who + when).

## Spring Boot stack & best practices (locked 2026-08-05)

### Persistence
- **Spring Data JPA (Hibernate) for CRUD entities** + **Flyway** migrations (versioned SQL in CI)
- **All reporting/balance queries = explicit JPQL or native SQL** — money reports computed in Postgres, never summed in Java
- Rejected: jOOQ (paid license commercial, overkill), JdbcTemplate (boilerplate), Spring Data JDBC (weaker ecosystem)
- Money: BIGINT minor units (fils) + currency_code, never floats

### Security
- Spring Security + `spring-security-oauth2-resource-server`: JWT access (short TTL ~15 min) + rotating refresh tokens stored server-side
- **Argon2** password hashing (Spring built-in encoder; bcrypt fallback)
- `@PreAuthorize("hasRole('EDITOR')")` for role checks

### API layer
- DTOs only in/out (Java 21 **records**), **MapStruct** mapping, **springdoc-openapi** (swagger-ui)
- `/api/v1` prefix, **RFC 7807 problem+json** errors via one `@RestControllerAdvice`
- Bean Validation on every DTO (`jakarta.validation`)
- `@Version` optimistic locking on `cash_days` (prevent double-posting same day)

### Testing
- JUnit 5 + **Testcontainers** (real Postgres — no H2, it drifts), MockMvc API tests, AssertJ

### Ops
- Actuator + **Prometheus** metrics (`/actuator/prometheus`), structured JSON logs + correlation ID
- Spring profiles dev/prod, `@ConfigurationProperties`, secrets via env vars only
- Multi-stage Dockerfile (Maven build → slim JRE), **GitHub Actions** CI: test → image → deploy

### Structure
- **Package-by-feature**: `user/`, `parking/`, `cash/`, `pettycash/`, `transfer/`, `common/`
- No Lombok (records cover it), no microservices/Redis/MQ/QueryDSL at this scale — boring well-tested monolith

## Architecture
- VPS cloud: Postgres + Spring Boot REST API + Next.js frontend (responsive).
- Frontend talks REST/JSON to API. Auth via JWT.
- Spring: Spring Boot 3 (Java 21), Spring Security + JWT, Spring Data JPA, Flyway migrations, Maven. Tests: JUnit + Testcontainers (optional) or H2 for unit.

## Schema (Postgres)

### Core
- `users` — id, username (unique), password_hash, display_name, role (EDITOR/VIEWER), active, created_at
- `books` — id, name, currency_code, created_at  (seed: "Shop", "Parking")
- `currencies` — code (PK), name, symbol, decimal_places (future multi-currency)
- `audit_log` — id, user_id, action, entity, entity_id, old_value, new_value, created_at

### Petty cash (file 1)
- `petty_cash_transactions` — id, book_id, date, description, type (PUT/TAKE), amount_minor, currency_code, entered_by, created_at
- Balance = SUM(put) − SUM(take) — **always computed**, never stored.

### Cash & deposits (file 2)
- `cash_days` — id, book_id, date, sales_minor, extra_minor, withdraw_minor, deposit_minor, deposit_remarks, ref, notes, entered_by, created_at
- UNIQUE(book_id, date). Balance computed: opening + sales + extra − withdraw − deposit.

### Parking (file 3)
- `parking_bills` — id, book_id, plate_no, amount_minor, payment_method (CASH/CARD), billed_at, entered_by
- `parking_cash_moves` — id, book_id, date, type (OPENING/TRANSFER_TO_SHOP/SALARY/EXPENSE/CLOSING), amount_minor, description, entered_by
- `parking_salary_payments` — id, move_id (FK), person, amount_minor  (Iqpal/Habib/Raseem split)
- `parking_bookings` — id, book_id, plate_no, monthly_rate_minor, renewal_month, active

### Transfers (cross-book)
- `transfers` — id, from_book_id, to_book_id, date, amount_minor, currency_code, ref, entered_by, created_at

## Cross-module automations (kill the Excel bugs)
1. **Petty cash take = cash withdrawal** — one form action posts `petty_cash_transactions` PUT + `cash_days.withdraw_minor` in the Shop book. No more manual double-entry (kills MAR 35.60 mismatch, Apr 13/4 60.00 imbalance).
2. **Parking → shop transfer** — one action creates `transfers` + `parking_cash_moves` (TRANSFER_TO_SHOP) + `cash_days.extra_minor`. The 12,500 flows are one click.
3. **Deposit sanity check** — API flags when deposit ≠ implied cash (net before deposit), surfaces warnings instead of silent drift.
4. **Date pickers everywhere** — "28\6\2926" typo and out-of-order rows become impossible.

## Screens
- Login
- Dashboard (per book: today's parking total cash/card, cash balance, petty cash balance)
- Parking: bill entry (plate + amount + cash/card), day book list, reports (today/yesterday/week/month/custom, cash vs card, per-plate)
- Parking: cash statement (opening, transfers, salaries, closing)
- Parking: bookings (renewals list, due flags)
- Cash: daily sheet entry + history + balance
- Petty cash: ledger + balance + top-up/take form
- Transfers: list + new transfer
- Users (editor only): CRUD, roles, activate/deactivate
- Audit log (viewer can see; editor manages)

## Reports
- Each module: daily / monthly / all-time totals, Excel export (they live in Excel today)
- Parking: today / yesterday / this week / custom range, cash-card split

## Data migration
- Parse the 3 existing xlsx (already analyzed: petty cash 5 sheets FEB–JUN 2026, cash deposit 10 sheets Sep 2025–Jun 2026, parking day book Jun 2026 616 rows + bookings 127) → seed script into Postgres as history.

## Open questions / next steps
- Repo name + location (`/data/Projects/<name>`?) — suggest `emad-ledger`
- Frontend: Next.js OK? (he said "web probably or tauri desktop")
- Maven (proposed) vs Gradle
- Seed the real Excel history on day 1, or start fresh?

⛔ No code until Abdullah says "go ahead".
