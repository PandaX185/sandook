# Feat — Domain schema (V2) + Cash module

**Date:** 2026-08-06 · **Status:** Approved (Abdullah: "go ahead step 1 and 2") · **Repo:** sandook

## Step 1 — V2 migration: full domain schema

Creates all tables from `brain-cash-ledger.md` (design already locked):

- `currencies` — code PK (seed: AED)
- `books` — name unique, currency_code FK (seed: Shop, Parking)
- `cash_days` — book_id, date, sales/extra/withdraw/deposit (BIGINT fils), deposit_remarks, ref, notes, entered_by; **UNIQUE(book_id, date)**
- `petty_cash_transactions` — PUT/TAKE ledger (step 3 uses it)
- `parking_bills` / `parking_cash_moves` / `parking_salary_payments` / `parking_bookings` (step 4 uses them)
- `transfers` — cross-book, CHECK from != to (step 5 uses it)
- `audit_log` — action/entity/old/new JSONB (step 6 wires it)

Money = BIGINT minor units (fils), never floats. All amounts `CHECK >= 0` (or > 0).

## Step 2 — Cash module (daily sheet)

Package `cash/` + `book/` (books needed as scope root).

**Endpoints** (`/api/v1/books/{bookId}/cash-days`):
- GET list → each row + computed `balanceMinor` (window function in Postgres — no Java summing)
- GET one → + balance
- POST create → EDITOR, sets entered_by, returns + warnings
- PUT update → EDITOR
- DELETE → EDITOR
- GET `/api/v1/books` → books list (for UI scope picker)

**Balance math** (matches Excel convention):
- `netCash = sales + extra − withdraw` (pre-deposit)
- `balance = opening + netCash − deposit`, where opening = cumulative net of all previous days (computed, never stored)

**Deposit sanity check** (kills the silent-drift bug class — Sep 0.50, Apr 60.00):
- when deposit > 0 and deposit ≠ cash-on-hand before deposit (prev balance + net) → non-blocking warning in response (`warnings[]`), 200 still returned

**Errors:** NotFound (404 book/day), Conflict (409 duplicate book+date), validation 400 — via existing `ApiExceptionHandler` + ProblemDetail.

**Tests:** Testcontainers + MockMvc (mirrors `AuthFlowIntegrationTest`): CRUD, running balance across days, deposit warning, duplicate-date 409, viewer 403 on writes, unknown book 404.

**No frontend, no petty-cash/parking/transfer code** — those are later steps.

## Step 3 — Petty cash module (done 2026-08-06)

- `pettycash/` package: entity + repo (running balance via window function, `totalBalance`, `balanceAsOf` — all Postgres-side), service, controller, records
- Endpoints under `/api/v1/books/{bookId}/petty-cash`: `GET/POST/PUT/DELETE transactions`, `GET balance?asOf=`
- **Automation #1 (linked top-up):** POST with `type=PUT` auto-adds the amount to `cash_days.withdraw_minor` for that book+date (creates the day row if missing, prunes it if a reversal empties it) — kills the manual double-entry that caused MAR 35.60 / Apr 60.00 mismatches. Response exposes `linkedCashDayId` + `linkedCashDayWithdrawMinor`. `TAKE` never touches cash days.
- PUT/DELETE of a linked PUT reverses the withdraw (ConflictException if the day's withdraw can't cover the reversal).
