# feat-statement-upgrade — Enriched parking statement + notes display

**Date:** 2026-08-10 · **Status:** Approved (go ahead 2026-08-10 02:10) · **Owner:** project lead
**Repo:** `/data/Projects/sandook` · **Phase:** 2 of the parking upgrade (sequencing in `brain-parking-upgrade.md`)
**Prereq:** `9b55251` (feat-booking-upgrade) — parking_bills.booking_id exists (V4)

## Goal

Turn the daily parking statement into the full detail view project lead described:

- Header cards: **total balance (cash+card)**, today's cash, today's card, **month total**, cumulative balance to date
- Day rows: opening | cash bills | card bills | total bills | **bookings payments** (bills with `booking_id`) | transfers to shop | salaries | expenses (**with notes**) | net out | closing | **cumulative balance**
- Cash-move descriptions shown inline (esp. EXPENSE) — satisfies "cash out with notes" visibility
- CLOSING moves stay informational (never change balance) — unchanged
- Backend validation: **description required for EXPENSE and SALARY** moves (cash out must have notes)

## Locked decisions (from brain doc Q&A)

- **Balance includes card** — "total balance" = cash + card combined. Day closing becomes `opening + cashBills + cardBills − netOut`.
- Bookings revenue = bills with `booking_id IS NOT NULL`, its own column.
- Statement stays **computed in the service (Java aggregation)** — extending the existing `ParkingCashMoveService.statement()` pattern; repository projections feed it (existing convention in this codebase, keeps diff small vs a full SQL rewrite).
- No schema change → **no Flyway migration** for this phase.
- Expense notes = non-blank `description` of EXPENSE moves that day, as a list on the day row.
- `todayCashMinor`/`todayCardMinor`/`monthBillsMinor` are nullable — null when today/current month not covered by the range (frontend shows "—").
- Currency stays in minor units (fils); frontend helpers `filsToAedWithCurrency`.

## API shape (extends existing endpoint, no new routes)

`GET /api/v1/books/{bookId}/parking/cash-moves/statement?from=&to=` → 200

```jsonc
{
  "bookId": 1,
  "summary": {
    "totalBalanceMinor": 15814,     // last day closing (cash+card combined)
    "todayCashMinor": 3000,         // null if today not in range
    "todayCardMinor": 400,          // null if today not in range
    "monthBillsMinor": 15814        // cash+card bills in current month ∩ range, null if none
  },
  "days": [
    {
      "date": "2026-06-01",
      "openingMinor": 0,
      "cashBillsMinor": 3000,
      "cardBillsMinor": 400,
      "totalBillsMinor": 3400,
      "bookingsMinor": 2500,        // bills linked to a booking
      "transfersToShopMinor": 0,
      "salariesMinor": 0,
      "expensesMinor": 0,
      "expenseNotes": [],           // EXPENSE descriptions that day
      "netOutMinor": 0,
      "closingMinor": 3400,         // opening + totalBills − netOut
      "cumulativeMinor": 3400,      // running balance (= closing)
      "warnings": []
    }
  ]
}
```

## Changes

### Backend (4 files)

1. **`ParkingBillDayTotal.java`** — extend projection: `getCashMinor()`, `getCardMinor()`, `getBookingsMinor()` (rename query accordingly).
2. **`ParkingBillRepository.java`** — `totalsByDay(bookId, from, to)`: per-day `SUM(CASE payment_method='CASH')`, `SUM(CASE payment_method='CARD')`, `SUM(CASE booking_id IS NOT NULL)`.
3. **`ParkingCashStatement.java`** — add `Summary` record + new `DayRow` fields (`cardBillsMinor`, `totalBillsMinor`, `bookingsMinor`, `expenseNotes`, `cumulativeMinor`).
4. **`ParkingCashMoveService.java`** — `statement()`: use new totals query, compute summary (total balance = last closing; today/month only when in range), collect expense notes per day, closing now includes card.
5. **`ParkingCashMoveRequest.java`** — `@AssertTrue`: EXPENSE/SALARY require non-blank `description` (400 via validation). Service unchanged (validation-only).

### Tests (`ParkingFlowIntegrationTest.java`)

- `statementMatchesExcelConvention` → rename `statementIncludesCardAndBookings`, fixtures get descriptions; assert card column, combined closing, bookings column (create a booking + pay it to link a bill), summary block.
- `closingMismatchWarns` — unchanged semantics, keep.
- New: `cashOutRequiresNotes` — EXPENSE/SALARY without description → 400; OPENING without description still OK.
- Existing `createMove` fixtures for EXPENSE/SALARY gain descriptions (only where backend now requires them).

### Frontend (2 files)

1. **`frontend/src/lib/types.ts`** — `ParkingStatement` gains `summary`; `ParkingStatementDayRow` gains `cardBillsMinor`, `totalBillsMinor`, `bookingsMinor`, `expenseNotes: string[]`, `cumulativeMinor`.
2. **`frontend/src/app/parking/Parking.tsx`** — `StatementTab` rewrite:
   - Header cards row (4 stat cards): Total balance (cash+card), Today cash, Today card, Month total — `filsToAedWithCurrency`, "—" for nulls.
   - Table columns: Date | Opening | Cash | Card | Bookings | → Shop | Salaries | Expenses (+notes) | Net out | Closing | Cumulative
   - Expense notes rendered as small lines under the expenses amount.

## Verification

- `make backend-test` (Testcontainers) — all green
- `npm run lint` + `npm run build` in frontend — clean
- Commit + push to `main`

⛔ No code until project lead says "go ahead" — **status: go ahead given 2026-08-10 02:10**.
