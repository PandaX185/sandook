# Feat — Frontend phase 2 (parking, transfers, audit, users)

**Date:** 2026-08-06 · **Status:** Approved (project lead: "do audit log and Frontend phase 2") · **Repo:** sandook

## Pages

### `/parking` (book-scoped via header selector, `/api/v1/books/{bookId}/parking/...`)
Tabs: **Bills** · **Statement** · **Bookings**
- **Bills**: entry form (plate, amount AED, CASH/CARD toggle, date) + summary stat cards (cash/card/total/count, from/to filters) + table with edit/delete
- **Statement**: per-day rows — opening, cash bills, transfers→shop, salaries, expenses, closing + warnings banner (matches Excel convention)
- **Bookings**: add/edit form (plate, monthly rate, renewal month, active) + list with due badge (red = due, green = active/paid)

### `/transfers` (`/api/v1/transfers`, global)
- New transfer form: from book, to book, date, amount, ref, **"Link parking → shop" checkbox** (the one-click 12,500 flow)
- List (all books) with linked badge + linked cash-day extra, edit/delete

### `/audit` (`/api/v1/audit`)
- Table: timestamp, username, action badge (CREATE green / UPDATE amber / DELETE red), entity, entity_id, old/new values in collapsible `<details>` (JSON pretty-printed)
- Filters: entity + action selects

### `/users` (`/api/v1/users`, EDITOR only — nav item hidden for VIEWER)
- Simple table: username, role badge, active

## Nav (AppShell)
Dashboard · Cash sheet · Petty cash · **Parking** · **Transfers** · **Audit** · **Users (EDITOR only)**

## Types (lib/types.ts)
ParkingBill(+Input), ParkingBillSummary, ParkingCashMove(+Input, SalaryPayment), ParkingStatement(+Day), ParkingBooking(+Input), Transfer(+Input), AuditEntry, User

## Verification
`npm run build` clean, backend suite still green (shared API changes: none — audit is additive).
