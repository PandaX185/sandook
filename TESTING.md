# Sandook (صندوق) — Testing Guide for Tester / Client

> A step-by-step guide to test the cash ledger app. No technical knowledge needed —
> follow the sections in order and write down anything that doesn't match the
> **Expected** result.

---

## 1. What this app does

Sandook replaces the 3 Excel files with one system:

- **Shop book** — daily cash sheet (sales, extra income, withdrawals, deposits)
- **Parking book** — bills, cash statement (openings, transfers, salaries, expenses), monthly bookings
- **Petty cash** — top-ups and takes, always linked to the cash sheet
- **Transfers** — money moved between the two books, **one click does the full entry**
- **Audit log** — every change is recorded (who, when, what)
- **Users** — two roles: `EDITOR` (full access) and `VIEWER` (read-only)

All money is stored in **fils (AED = 100 fils)** — amounts will always be exact
(e.g. `12.35`), never rounded weirdly like `12.34999`.

---

## 2. Starting the app

```bash
cd /data/Projects/sandook
make setup     # first time only (creates .env, installs deps, builds & starts)
make up        # start the app (use this every time after)
make logs      # (optional) watch logs in real time
```

Open the app: **http://localhost:3000**

Backend (if needed): http://localhost:8081 · Database: port 5433

---

## 3. Login

| Step | Expected |
|---|---|
| Open http://localhost:3000 | Redirected to the login page |
| Login with `admin` and the password from `.env` (created by `make setup`) | Land on the Dashboard |

**If login fails with "invalid credentials":** the bootstrap admin is only created
when `ADMIN_PASSWORD` is set in `.env`. Check the file exists and the backend has
started (`make logs-backend`).

**Test wrong password:** type an incorrect password → clear error message, no crash.

---

## 4. Roles — EDITOR vs VIEWER

1. Log in as `admin` (EDITOR). Go to **Users** → create a second user, e.g.
   `tester` / `Test@123`, role **VIEWER** (password must be at least 8 chars).
2. Log out, log in as `tester` / `Test@123` (VIEWER).

| Screen | As EDITOR | As VIEWER |
|---|---|---|
| Cash sheet, Petty cash, Parking, Bookings, Transfers | Add / Edit / Delete buttons visible | **No forms, no edit/delete** — read-only lists |
| Users page | Create / edit / deactivate users | List only (read-only) |

**Expected:** a VIEWER can never create, edit, or delete anything. If they can, that's a bug.

---

## 5. Dashboard

The dashboard shows the **selected book** (Shop or Parking — there's a book
switcher). Expected:

- Cash balance (last day's balance)
- Today's net cash (if today's row exists)
- Petty cash balance

Switch between Shop and Parking → numbers change accordingly.

---

## 6. Daily cash sheet (Shop book)

Formula shown in the app: **balance = opening + sales + extra − withdraw − deposit**

**Test with hand-checkable numbers** (use the Shop book):

1. Add: date = today, Sales = `1000`, leave the rest 0 → **Expected:** Net `1000`, Balance `1000`.
2. Add another day: Sales = `500`, Extra = `200`, Withdraw = `300`, Deposit = `100` →
   **Expected:** Net = 500+200−300−100 = **300**, Balance = 1000+300 = **1300**.
3. Edit the second day: change Sales to `600` → **Expected:** Net `400`, Balance `1400`.
4. Try to add a **second entry for the same date** → **Expected:** rejected (one row per day).
5. Put a deposit amount → the row shows an amber **deposit** badge.
6. Enter values where deposit ≠ implied cash → **Expected:** a warning banner appears
   (deposit sanity check). The entry is still saved, but the warning tells you.
7. Negative balance → shown in **red**.
8. Delete a day → row disappears and the running balance recalculates.

**Tip:** keep a small calculator handy and verify every balance by hand — this is the
heart of the app. Any wrong running balance = critical bug.

---

## 7. Petty cash

Rule: **balance = all top-ups (PUT) − all takes (TAKE)**.

1. Add a **TAKE** of `50` → balance shows `-50` (money left petty cash).
2. Add a **PUT (top-up)** of `100` → balance shows `50`.
3. **The automation:** the PUT should show a message like
   *"Linked: AED 100 added to the cash sheet withdrawal"*.
   → Go to the Cash sheet, same date: **Withdraw must be `100`**. One entry, two ledgers.
4. Edit the PUT amount to `150` → cash sheet withdrawal updates to `150`.
5. Delete the PUT → cash sheet withdrawal for that day goes back to `0`.

**Expected:** petty cash balance always equals top-ups minus takes, and every top-up
is mirrored in the cash sheet. If they ever disagree → bug.

---

## 8. Parking — bills

1. Add a bill: Plate `A12345`, Amount `25`, method **CASH** → badge shows CASH (green).
2. Add another: Plate `B67890`, Amount `40`, method **CARD**.
3. **Filters:** set From = today, To = today → both bills appear; summary cards show
   Cash `25`, Card `40`, Total `65`, Bills `2`.
4. Filter by plate `A12345` → only that bill; summary cards recalculate.
5. Edit a bill (change amount 25 → 30) → totals update.
6. Delete a bill → totals update.

**Expected:** summary numbers always match the filtered list, cash and card never mix.

---

## 9. Parking — statement & cash moves

Move types: OPENING, TRANSFER_TO_SHOP, SALARY, EXPENSE, CLOSING.

1. Add an **OPENING** move of `500`.
2. Add a **SALARY** move of `100` → balance drops to `400`.
3. Add an **EXPENSE** of `50` → balance `350`.
4. Add a **CLOSING** of `350` → statement balances out to 0.
5. Check the **Daily statement** view (Excel convention) — numbers match the moves.

**Expected:** statement always reconciles: opening + transfers − salaries − expenses − closing = 0.

---

## 10. Parking — bookings (monthly parking)

1. Add a booking: Plate `C33445`, Monthly rate `300`, Renewal month = this month.
2. **Expected:** badge shows **Due** (red) if this month is the renewal month, otherwise **Active** (green).
3. Filter by status (All / Due / Active) → correct lists.
4. Edit the rate or renewal month → badge updates accordingly.

---

## 11. Transfers (the 12,500 flow, now one click)

1. Go to **Transfers**. From book = **Parking**, To book = **Shop**, Amount `12500`, date = today.
2. Tick the **link parking move** checkbox and save.
3. **Expected — the automation:**
   - Transfer appears in the list, marked **linked**
   - Parking statement: a **TRANSFER_TO_SHOP** move of `12500` appears
   - Shop cash sheet: **Extra = 12500** for that date (row auto-created if missing)
4. **Validation:** try From = To (same book) → **Expected:** error "must be between two different books".
5. Try linking a transfer from **Shop** → **Expected:** error (linked moves only from Parking).
6. Edit the transfer amount → **Expected:** both the parking move and the shop extra update.
7. Delete the transfer → **Expected:** both linked entries are removed/reversed.

**This is the most important test.** The whole point of the app is that the 12,500
transfer is one click and the three ledgers stay in sync. Any mismatch = critical bug.

---

## 12. Audit log

1. Go to **Audit**.
2. Every action you did above (login, add/edit/delete) should appear: **who, when, what**.
3. Log in as `tester` (VIEWER) → audit log still visible (read-only).

**Expected:** every create/edit/delete is recorded; nothing silently changes.

---

## 13. Users

As EDITOR:

1. **Create** a user (`tester` / `Test@123`, role VIEWER) → appears in the list,
   and the new user can log in.
2. **Edit** the user → change role to EDITOR, reset the password
   (leave the password field blank to keep the current one).
3. **Deactivate** the user → log out, try logging in as them → **Expected:**
   rejected with "invalid username or password". Reactivate → they can log in again.
4. **Duplicate username** → **Expected:** error "Username already exists".
5. **Short password** (fewer than 8 chars) → **Expected:** error, not saved.
6. **Safety rules** (server-enforced, error message shown):
   - You cannot deactivate or demote **your own** account
   - The **last active EDITOR** cannot be deactivated or demoted
7. No delete button anywhere — accounts are deactivated, never deleted (keeps
   the audit trail intact).
8. Audit log shows a CREATE/UPDATE entry for every user change.

---

## 14. Quick regression checklist (run after any fix)

- [ ] Login works, wrong password rejected with a clear message
- [ ] VIEWER cannot edit anything (see Section 4 for creating a VIEWER)
- [ ] Cash sheet: hand-verified balance across 3+ days, one entry per day
- [ ] Petty cash PUT mirrors into cash sheet withdrawal; TAKE doesn't
- [ ] Parking summary (cash/card/total) matches filtered bills
- [ ] Transfer Parking→Shop updates parking statement + shop extra, and reverses on delete
- [ ] Audit log shows the changes you just made

---

## 15. Known gaps (NOT in this version — don't report as bugs)

- ❌ Reports & Excel export (daily/monthly totals, per-plate) — planned, not built yet
- ❌ Importing the old Excel history — planned, not built yet
- ❌ Booking renewal automation (auto-invoice renewals) — bookings can be created/edited manually only
- ❌ Arabic UI — English only for now

---

## 16. How to report a problem (bug report template)

```
1. What were you doing? (screen + exact steps)
2. What did you type / select? (dates, amounts, plate numbers)
3. What did you expect to happen?
4. What actually happened? (paste the error message if any)
5. Role you were logged in as (EDITOR / VIEWER)
6. Screenshot, if possible
```

---

*Guide written 2026-08-07 for sandook v0.2 (phase 1+2). Update it whenever features ship.*
