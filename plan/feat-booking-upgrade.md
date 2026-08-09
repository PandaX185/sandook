# Feat — Bookings Upgrade (intervals, status, pay button)

**Date:** 2026-08-10 · **Status:** Approved ("go ahead" 2026-08-10 01:07) · **Owner:** Abdullah
**Repo:** `/data/Projects/sandook` · **Branch:** main
**Source:** `plan/brain-parking-upgrade.md` (all decisions locked)

## Scope
1. Bookings support intervals: MONTHLY / THREE_MONTHS / SIX_MONTHS / CUSTOM
2. Fix status computation (DUE = last day of paid period, no grace)
3. Pay button: ONE bill for full amount, custom amount, cash/card
4. Payment history per booking

## Locked rules
- DUE = today equals the last day of the paid-through period (monthly → last day of paid month; 3M → last day of 3rd month)
- Before that day = PAID; after = OVERDUE; inactive = INACTIVE
- Never-paid booking: due when today ≥ next_due_date
- One payment = one bill (full amount), amount editable (discounts/custom), cash or card
- Column rename: `renewal_month` → `next_due_date` (+ `paid_through_date`, `interval_type`, `interval_months`, `parking_bills.booking_id`)

## Migration V4 (Flyway)
```sql
ALTER TABLE parking_bookings RENAME COLUMN renewal_month TO next_due_date;
ALTER TABLE parking_bookings ADD COLUMN interval_type VARCHAR(20) NOT NULL DEFAULT 'MONTHLY'
    CHECK (interval_type IN ('MONTHLY','THREE_MONTHS','SIX_MONTHS','CUSTOM'));
ALTER TABLE parking_bookings ADD COLUMN interval_months INT;
ALTER TABLE parking_bookings ADD COLUMN paid_through_date DATE;
ALTER TABLE parking_bills ADD COLUMN booking_id BIGINT REFERENCES parking_bookings(id) ON DELETE SET NULL;
CREATE INDEX idx_parking_bills_booking ON parking_bills(booking_id);
```
- Backfill: existing rows keep `next_due_date` = old renewal_month, interval MONTHLY, paid_through NULL → show DUE/OVERDUE until paid (honest)
- `interval_months` NULL except CUSTOM (service derives 1/3/6 for the others)

## Backend
- **`ParkingBookingInterval`** enum: MONTHLY(1), THREE_MONTHS(3), SIX_MONTHS(6), CUSTOM(0) + `months(Integer custom)` helper
- **`ParkingBooking`**: `renewalMonth` → `nextDueDate`; add `intervalType`, `intervalMonths`, `paidThroughDate`
- **`ParkingBill`**: add `bookingId` (nullable, column `booking_id`) + row projection field
- **`ParkingBookingRequest`**: `plateNo, monthlyRateMinor, intervalType, intervalMonths?, nextDueDate, active?`; validation — CUSTOM requires intervalMonths 1–24
- **`ParkingBookingStatus`** enum: INACTIVE / PAID / DUE / OVERDUE (computed in service — dataset is small, date logic not money math)
- **`ParkingBookingResponse`**: replaces `due` boolean with `status` + interval fields + paidThroughDate
- **Pay flow** (`POST /bookings/{id}/pay`, EDITOR, `{ amountMinor, paymentMethod, paidAt? }`):
  1. months = interval months (derived or custom)
  2. create ONE `parking_bill` (booking_id set, plate from booking, billedAt = paidAt or today)
  3. advance booking: `paidThroughDate = nextDueDate.plusMonths(months).minusDays(1)`, `nextDueDate = nextDueDate.plusMonths(months)`
  4. audit bill CREATE + booking UPDATE, one transaction
- **`GET /bookings/{id}/payments`** → linked bills (history)
- **List**: params `active` + `status` (replaces `dueWithinMonths`); status filter applied in service
- **Repo**: rename methods to `NextDueDate`; add `findByBookingIdOrderByBilledAtAscIdAsc`

## Frontend (Parking.tsx BookingsTab + types.ts)
- `ParkingBooking` type: status/interval/nextDueDate/paidThroughDate
- `ParkingBookingInput`: interval fields; `ParkingBookingPayInput`: amountMinor, paymentMethod, paidAt
- Form: interval select (Monthly/3M/6M/Custom) + months input when CUSTOM + next due date
- Table: colored status badge (PAID green / DUE amber / OVERDUE red / INACTIVE gray)
- Pay button (EDITOR): dialog — default amount = monthlyRate × months, editable, CASH/CARD toggle, date default today
- Payment history: expandable row fetching `GET /{id}/payments`
- Invalidate bookings + bills queries after pay

## Tests (ParkingFlowIntegrationTest — Testcontainers + MockMvc)
- Create booking with CUSTOM interval
- Pay flow: one bill created (booking_id, custom amount, method), dates advanced correctly
- Status transitions: PAID → DUE (last day) → OVERDUE
- Never-paid → DUE/OVERDUE; inactive → INACTIVE
- Validation: CUSTOM without intervalMonths → 400

## Verify
- `make backend-test` (needs Docker)
- `make frontend-lint` / `make frontend-build`
- Commit + push to main

⛔ Code approved — proceeding.
