# Brain — Parking Upgrade (bills, statement, bookings, Excel)

**Date:** 2026-08-10 · **Status:** Brainstorm (no code yet) · **Owner:** Abdullah
**Repo:** `/data/Projects/sandook` — parking module of the cash ledger

## Scope (from Abdullah's notes, refined)

1. Parking bills: show all days, fix filter flickering
2. Statement: full detail view — balance total, cumulative, today, cash/card, bills, bookings, cash out
3. Bookings: monthly / 3 months / 6 months / custom interval, fix status, pay button with custom amount + cash/card
4. Parking cash out with notes
5. Notifications of due bills (in-app banners only) + Excel import/export matching the original 3 sheets

## Locked decisions (from Q&A)

- **Filter bug = frontend flicker** on every keystroke in the search input → debounce fix
- **Statement balance includes card** — "balance total" = cash + card combined
- **Booking pay = ONE bill for the full amount** (e.g., 3 months → one bill of rate × 3), not 3 bills
- **Status bug:** bookings currently show "due" whenever renewal month ≤ now + 2 months — too aggressive (shows due even when the date is tomorrow/future). Needs a correct computed status
- **DUE timing (locked):** a booking becomes DUE on the **last day of its paid period** — for monthly, the last day of the paid month; same pattern for 3/6/custom intervals. Before that = PAID; after that = OVERDUE
- **Cash out must have notes** (description)
- **Notifications:** in-app banners ONLY — no Telegram/WhatsApp/email push
- **Excel:** import + export must match the exact layout of the original sheets. **Export = ALL sheets** (parking day book + cash statement + booking sheet **+ petty cash + cash deposit formats**). **Import = all sheets too**, same 3 original file layouts

## Current state (verified in code)

- `parking_bills`: plate_no, amount_minor, payment_method (CASH/CARD), billed_at — filters from/to/plate exist in API, UI flickers
- `parking_bookings`: plate_no, monthly_rate_minor, renewal_month (single LocalDate), active — **no payment history, no link to bills**
- "Due" today = `active && renewal_month <= firstOfMonth(+2)` — always true for most bookings → status is meaningless
- `parking_cash_moves`: date, type (OPENING/TRANSFER_TO_SHOP/SALARY/EXPENSE/CLOSING), amount, description (optional, 255)
- Statement (`ParkingCashStatement`): per-day rows — opening, cashBills, transfers, salaries, expenses, netOut, closing. **No cumulative, no card split, no bookings revenue, no notes shown**

## Original Excel formats (from media inbound, verified 2026-08-10)

### 1. IMAD_ALSHAER_CAR_PARKING_6...2026.xlsx — 3 sheets

**Day Book** (1522 rows): `DATE | CAR NUMBER | DURATION | TERM | CASH | CARD | PAYMENT STATUS`
- Example: `1\6\2026 | 79953-SHA-4 | | MONTHLY RENIEW | | 400 | P`

**cash statement** (44 rows): `Date | Amount | Remarks | Balance`
- Remarks carry semantic markers: "OPENING BALANCE", "transfer money to sh 12500 BALANCE(314)", "CASH IN HAND" → running balance in text form

**BOOKING SHEET** (1078 rows): `SL.NO | Car Plate Number | Due date FROM | Due date TO | Total Price | monthly amount | Term (DURATION OF RENT) | Payment status`

### 2. Cash_Deposit_Cash_in_Hand_2026.xlsx — 10 monthly sheets (Sep 2025–Jun 2026)

`Date | Sales Amount | Extra Amount take fr | Withdraw | Net Cash | Deposit Amount | Balance | Deposit Remarks | Reference/Receipt No | Notes`
- Opening balance row, then per-day rows; "DEPOSITED BY TAIMOOR/AKASH" remarks

### 3. Petty_Cash.xlsx — monthly sheets (FEB–JUN 2026)

`Date | Amount | Remarks | Balance`
- Remarks: "OPENING BALANCE", "Put Money \"1497.25\"", "TAKEN PETTY CASH BY Mr.M..." → balance text like `150 (BALANCE 647.5)`

## Design

### A. Parking bills — all days + filter fix

- **UI:** default = all bills grouped by day (like Excel day book), newest first, day subtotals (cash/card), paginated (server-side, e.g. 50/day groups)
- **Flicker fix:** debounce the plate search input (~300 ms) + `useDeferredValue`/AbortController so stale responses don't clobber newer ones; keep previous rows while loading (no blank flash)
- **Filters:** date range + payment method (ALL/CASH/CARD) + plate, quick presets (Today / Yesterday / This Week / This Month / All) + Clear button
- Backend already supports `from/to/plate` — add `paymentMethod` filter param

### B. Statement — full detail view

Enrich `ParkingCashStatement` (computed in SQL, per the money-in-SQL rule):

- **Header cards:** total balance (cash+card), today's cash, today's card, month total, cumulative balance to date
- **Day rows:** opening | cash bills | card bills | total bills | bookings payments | transfers to shop | salaries | expenses (with notes) | net out | closing | **cumulative balance**
- **Bookings revenue** = bills linked to bookings (`booking_id`), shown as its own column
- Show cash move **notes/descriptions** inline (esp. EXPENSE) — this satisfies "cash out with notes" visibility
- Keep CLOSING moves informational (never change balance)

### C. Bookings — intervals, status, pay button

**Schema changes (Flyway migration):**
- `parking_bookings`: replace `renewal_month` semantics with:
  - `interval_type` VARCHAR: `MONTHLY | THREE_MONTHS | SIX_MONTHS | CUSTOM`
  - `interval_months` INT (for CUSTOM; others derive: 1/3/6)
  - `next_due_date` DATE (**renamed from `renewal_month`** — Abdullah: "as you see") — the next period start / due date
  - `paid_through_date` DATE (nullable — last covered period end, i.e. last day of the paid interval)
- `parking_bills`: add nullable `booking_id` FK → `parking_bookings(id)` (one bill = one payment, full amount)

**Status (computed, in SQL):**
- `INACTIVE` — active = false
- `PAID` — today < last day of paid-through period
- `DUE` — today = **last day of the paid-through period** (monthly: last day of the paid month; 3M: last day of the 3rd month; etc.)
- `OVERDUE` — today > last day of paid-through period
- Never-paid booking: due date = its start date → DUE on that day, OVERDUE after
- Fix = derive from `next_due_date` + `paid_through_date`, not the old "≤ now+2mo" hack

**Pay flow:**
- Pay button on booking → dialog: shows rate, interval, months covered, **default amount = rate × interval_months**, editable (discounts/custom), payment method CASH/CARD
- Submit → creates ONE `parking_bill` (booking_id set) + advances `next_due_date` by interval_months + sets `paid_through_date`
- Booking payment history = the linked bills (visible per booking)

### D. Cash out with notes

- Backend already has `description` — make it **required for EXPENSE/SALARY** (validation) and **display it in the statement** (B)
- UI: cash-out form gets a required Notes field + quick-fill chips (salary, maintenance, utilities…)

### E. Notifications (in-app banners only)

- Dashboard banner list: bookings due this week / overdue, maybe upcoming deposits
- Backend: `GET /api/v1/parking/notifications` (or dashboard endpoint) computing due/overdue from `next_due_date`
- Frontend: banner strip on dashboard + parking pages; no cron/push infra needed

### F. Excel import/export (match originals exactly)

**Export (5 files / 5 sheet layouts, same as originals):**
1. `parking_daybook.xlsx` — sheet "Day Book": DATE | CAR NUMBER | DURATION | TERM | CASH | CARD | PAYMENT STATUS
2. `parking_statement.xlsx` — sheet "cash statement": Date | Amount | Remarks | Balance (remarks = our move descriptions; balance = computed running)
3. `parking_bookings.xlsx` — sheet "BOOKING SHEET": SL.NO | Car Plate Number | Due date FROM/TO | Total Price | monthly amount | Term | Payment status
4. `cash_deposit.xlsx` — monthly sheets: Date | Sales Amount | Extra Amount take fr | Withdraw | Net Cash | Deposit Amount | Balance | Deposit Remarks | Reference/Receipt No | Notes
5. `petty_cash.xlsx` — monthly sheets: Date | Amount | Remarks | Balance

**Import (all 3 original layouts, preview-then-commit):**
- Parking day book → `parking_bills`; booking sheet → `parking_bookings`; cash statement → `parking_cash_moves`; plus petty cash + cash deposit sheets → their tables

## Sequencing (proposal)

1. **feat-booking-upgrade** — schema migration (interval, paid_through, booking_id on bills), status fix, pay button (biggest; touches DB)
2. **feat-statement-upgrade** — enriched SQL statement + header cards + notes display
3. **feat-parking-ui-polish** — bills all-days view + debounce/filter fix + cash-out notes UI + in-app banners
4. **feat-excel-import-export** — 3-file export + import w/ preview

## Open questions

- ~~DUE window~~ → **locked:** DUE = last day of paid-through period (per interval)
- ~~Export scope~~ → **locked:** all 5 sheet formats (parking 3 + petty cash + cash deposit)
- ~~Import scope~~ → **locked:** all sheets, 3 original layouts
- ~~Column rename~~ → **locked:** rename `renewal_month` → `next_due_date`

All decisions locked — ready to split into /feat plans on approval.

⛔ No code until Abdullah says "go ahead".
