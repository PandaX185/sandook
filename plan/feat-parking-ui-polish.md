# Plan — Parking UI Polish (phase 3)

**Date:** 2026-08-10 · **Status:** go ahead given (project lead: "go ahead phase 3") · **Repo:** `/data/Projects/sandook`
**Prereqs:** phase 1 (bookings) + phase 2 (statement) shipped — `9b55251`, `1f99489`

## Scope (from `brain-parking-upgrade.md`, sections A + D + E)

1. **Bills all-days view + filter fix** — day-book style grouping with per-day subtotals, payment method filter, quick presets, debounced plate search (no flicker)
2. **Cash-out notes UI** — the missing cash-move creation form, with required Notes for EXPENSE/SALARY + quick-fill chips (backend validation already in place from phase 2)
3. **In-app banners** — due/overdue booking notifications on dashboard + parking page (no external push)

## Locked decisions

- **Day grouping is client-side** — the flat bills API already returns everything in range; group by `billedAt` in the UI, render day header rows + subtotals (cash/card/total). No new grouped/paginated endpoint (data volume per month is small; revisit only if it grows)
- **Order: newest first** — Excel day book convention (current API sorts ASC; UI reverses client-side instead of touching the API contract)
- **`paymentMethod` filter added to backend** — `GET /bills?paymentMethod=CASH|CARD` AND to `summary` (so the header cards stay consistent with the filter)
- **Debounce = local hook** — 300ms `useDebouncedValue` on the plate input + `placeholderData: keepPreviousData` so rows never blank-flash; no AbortController needed (single in-flight fetch via the debounce)
- **Presets** — Today / Yesterday / This Week (Mon→today) / This Month (1st→today) / All + Clear button
- **Move form lives in StatementTab** — that tab already shows the moves list; add a "New cash move" card above it, EDITOR-only
- **Move types offered: OPENING, EXPENSE, SALARY, CLOSING** — TRANSFER_TO_SHOP is 409 read-only (used by transfers flow); SALARY gets dynamic person/amount rows with a running-sum check; EXPENSE/SALARY show required Notes + chips (Salary, Maintenance, Utilities, Cleaning, Rent, Other)
- **Notifications endpoint** — `GET /api/v1/books/{bookId}/parking/notifications` → list of `{ plateNo, status: OVERDUE|DUE_SOON, date }`:
  - OVERDUE: paid-through (or never-paid next-due) date < today
  - DUE_SOON: that same date within [today, today+7]
- **Banner UI** — shared `ParkingNotifications` component (amber/red items, link to /parking) on dashboard + top of parking page

## Backend changes

1. `ParkingBillRepository.search` + `summarize`: add nullable `paymentMethod` predicate (`b.payment_method = :paymentMethod` when non-null)
2. `ParkingBillService.list`/`summary` + `ParkingBillController`: pass through the new param
3. New `ParkingNotification` record + `ParkingBookingService.notifications(bookId)` + controller `GET /parking/notifications`
4. Tests: bills `?paymentMethod=` filter; notifications returns OVERDUE + DUE_SOON correctly (date-relative fixtures)

## Frontend changes

5. `types.ts`: add `ParkingNotification`; `ParkingCashMoveInput` already exists
6. `Parking.tsx`:
   - `useDebouncedValue` hook (top of file)
   - BillsTab: paymentMethod select, preset buttons, debounced plate, keepPreviousData, day-grouped table (day header + subtotal row, then bill rows, newest first)
   - StatementTab: "New cash move" card — type select, date, amount, description (required for EXPENSE/SALARY + chips), salary payment rows (SALARY), submit via `POST /cash-moves`; invalidate statement+moves
7. New `frontend/src/app/parking/ParkingNotifications.tsx` — banner list component
8. Dashboard + parking page: render the banner

## Verification

- `make backend-test` (Testcontainers, full suite)
- `npm run lint` + `npm run build`
- Commit + push to `main`

⛔ No code until project lead says "go ahead" — **granted 2026-08-10 03:03**.
