# Feat — Parking module + Transfers module (steps 4 & 5)

**Date:** 2026-08-06 · **Status:** Approved (project lead: "do step 1 and 2") · **Repo:** sandook
**Supersedes:** steps 4 & 5 of `feat-cash-days.md` (parking + transfers were left as future work there)

## Step 4 — Parking module (`parking/` package)

Tables already exist from V2 (`parking_bills`, `parking_cash_moves`,
`parking_salary_payments`, `parking_bookings`). This step adds the code.

### Endpoints (`/api/v1/books/{bookId}/parking/...`)

**Bills:**
- `GET /bills?from=&to=&plate=` — filtered list (all three optional)
- `GET /bills/summary?from=&to=` → `{cashMinor, cardMinor, totalMinor, count}` (Postgres SUM, never Java)
- `POST /bills` (EDITOR), `GET/PUT/DELETE /bills/{id}` (EDITOR writes)

**Cash moves:**
- `GET /cash-moves` — ordered by (date, id), each with running balance
- `GET /cash-moves/statement?from=&to=` — per-day statement, mirrors the Excel sheet:
  `opening + cashBills − (TRANSFER_TO_SHOP + SALARY + EXPENSE) = closing` — verified against
  real data: opening 12,814 + cash 24,615 − 12,500 − 5,333 − 192 − 12,500 = 6,904 ✓ (30/6/2026)
  - CLOSING rows are informational (don't change balance)
  - Warning when a recorded CLOSING ≠ computed closing (same philosophy as deposit sanity check)
- `POST /cash-moves` (EDITOR), `GET/PUT/DELETE /cash-moves/{id}` (EDITOR writes)
  - SALARY moves carry `salaryPayments: [{person, amountMinor}]`; Σ payments MUST equal `amountMinor` (400 otherwise)
  - `TRANSFER_TO_SHOP` is **read-only via API** — only the transfer automation creates it (409 on direct write) — kills double-entry

**Bookings:**
- `GET /bookings?active=&dueWithinMonths=` — list with `due` flag (active AND `renewal_month` ≤ end of next month)
- `POST /bookings`, `GET/PUT/DELETE /bookings/{id}` (EDITOR writes)

### Balance math (cash moves)
- Running balance = cumulative `OPENING − TRANSFER_TO_SHOP − SALARY − EXPENSE`, computed in Postgres (window function)
- CLOSING: recorded end-of-day snapshot, shown but excluded from balance

## Step 5 — Transfers module (`transfer/` package)

### Endpoints (`/api/v1/transfers`)
- `GET /transfers?bookId=&from=&to=` — list, optional book filter
- `POST /transfers` — `{fromBookId, toBookId, date, amountMinor, ref, linkParkingMove}`
- `GET/PUT/DELETE /transfers/{id}` — PUT updates date/amount/ref only (books immutable after creation)

### Automation #2 (linked parking→shop transfer — one click)
`POST` with `linkParkingMove=true` atomically creates:
1. the `transfers` row
2. `parking_cash_moves` row on the **from** book (type `TRANSFER_TO_SHOP`)
3. adds the amount to `cash_days.extra_minor` on the **to** book for that date
   (auto-creates the day row if missing, like petty cash; prunes it if reversal empties it)

PUT/DELETE of a linked transfer reverses both links (conflict 409 if the cash day's
extra can't cover the reversal). The 12,500 flow is now one click, recorded once,
reflected in all three ledgers.

### Errors
404 unknown book/transfer, 409 same-book transfer / linked-move write, 400 validation —
via existing `ApiExceptionHandler` + ProblemDetail.

## Tests (Testcontainers + MockMvc, mirrors `PettyCashFlowIntegrationTest`)

**Parking:** bill CRUD + filters + summary math, salary move Σ-validation, TRANSFER_TO_SHOP
write rejection, statement math (opening + cash − outflows = closing), booking due flag,
viewer 403 on writes, unknown book 404.
**Transfers:** create + linked move + extra_minor bump (auto-created cash day), plain
transfer (no links), delete reverses links, update moves links, same-book 409, viewer 403.

## Out of scope (later)
Parking reports/Excel export, frontend pages for parking + transfers, audit log wiring (step 6).
