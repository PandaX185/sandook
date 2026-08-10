# Plan — feat-excel-import-export

**Date:** 2026-08-10 · **Phase 4 of parking upgrade** · **Repo:** `/data/Projects/sandook` (main)
**Source:** `plan/brain-parking-upgrade.md` §F — all decisions locked, direct approval given ("do phase 4 too").

## Scope

### Export (5 files, layouts identical to originals)
1. `parking_daybook.xlsx` — sheet "Day Book": `DATE | CAR NUMBER | DURATION | TERM | CASH | CARD | PAYMENT STATUS`
   - Source: `parking_bills` (billed_at, plate_no, amount_minor, payment_method). CASH/CARD column per method; DURATION blank; TERM blank (not tracked); PAYMENT STATUS = "P".
2. `parking_statement.xlsx` — sheet "cash statement": `Date | Amount | Remarks | Balance`
   - Source: `parking_cash_moves` sorted by date; Amount = signed AED; Remarks = description (or type label); Balance = running.
3. `parking_bookings.xlsx` — sheet "BOOKING SHEET": `SL.NO | Car Plate Number | Due date FROM | Due date TO | Total Price | monthly amount | Term (DURATION OF RENT) | Payment status`
   - Source: `parking_bookings`; FROM = next_due_date, TO = paid_through_date, Total = monthly_rate × interval months, Term = interval label, status = computed (PAID/DUE/OVERDUE/INACTIVE).
4. `cash_deposit.xlsx` — one sheet per month (e.g. "Sep 2025"): `Date | Sales Amount | Extra Amount take fr | Withdraw | Net Cash | Deposit Amount | Balance | Deposit Remarks | Reference/Receipt No | Notes`
   - Source: `cash_days` (sales_minor, extra_minor, withdraw_minor, deposit_minor, deposit_remarks, ref, notes); Net Cash = sales + extra − withdraw; Balance = running.
5. `petty_cash.xlsx` — one sheet per month: `Date | Amount | Remarks | Balance`
   - Source: `petty_cash_transactions` (PUT = +, TAKE = −); Balance = running.

### Import (preview-then-commit; 3 original layouts + petty cash + cash deposit)
- `POST /imports/preview` (multipart .xlsx) → detect layout by headers, parse + validate every row, return `ImportPreviewResponse { layout, fileName, rows: [{rowNo, fields, valid, errors}] }`. **No writes.**
- `POST /imports/commit` (JSON: layout + valid rows) → transactional insert, return `{ inserted, skipped }`.
- Layout → table:
  - **Day Book** → `parking_bills` (CASH→CASH bill, CARD→CARD bill; skip rows where PAYMENT STATUS not P/empty)
  - **BOOKING SHEET** → `parking_bookings` (FROM→next_due_date, TO→paid_through_date, monthly→monthly_rate_minor, Term→interval_type, status→active)
  - **cash statement** → `parking_cash_moves` (type from remarks: OPENING BALANCE→OPENING, transfer→TRANSFER_TO_SHOP, salary→SALARY, else EXPENSE; remarks→description; Balance column ignored)
  - **cash deposit sheets** → `cash_days` (unique book+date; existing date → error row)
  - **petty cash sheets** → `petty_cash_transactions` (PUT/TAKE by sign/remarks; currency = book currency)

## Backend

- `pom.xml`: add `org.apache.poi:poi-ooxml` (Apache POI, xlsx via XSSFWorkbook).
- New `backend/src/main/java/com/sandook/ledger/excel/` package:
  - `ExcelExportService` — builds the 5 workbooks (uses existing services/repos: ParkingBillService.list, ParkingCashMoveService (moves), ParkingBookingService.list, CashDayRepository.findAllByBookIdOrderByDateAsc [add], PettyCashService.list).
  - `ExcelImportService` — layout detection, row parsing/validation, commit inserts.
  - `ExcelController` at `/api/v1/books/{bookId}/` :
    - `GET exports/daybook?from&to`, `GET exports/statement?from&to`, `GET exports/bookings`, `GET exports/cash-deposit?year`, `GET exports/petty-cash?year` → `ResponseEntity<byte[]>` + `Content-Disposition` attachment, `produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"`.
    - `POST imports/preview` (multipart, EDITOR), `POST imports/commit` (EDITOR, JSON).
- Money: parse Excel decimals with `BigDecimal` → minor (×100). No doubles.
- Dates: originals use `DD\MM\YYYY` — accept `/`, `\`, `-`, Excel date cells.
- Auth: exports readable by both roles (GET, no PreAuthorize); imports EDITOR-only.

## Frontend

- `frontend/src/lib/api.ts`: add `downloadFile(path, filename)` — fetch with Bearer token, blob, trigger anchor download (api() is JSON-only; downloads need blob).
- `frontend/src/lib/types.ts`: `ImportPreviewRow`, `ImportPreviewResponse`, `ImportCommitResponse`.
- Parking page: toolbar row with 5 export buttons (Day Book / Statement / Bookings / Cash Deposit / Petty Cash — date/year from current filters where applicable) + "Import Excel" button opening a modal:
  - choose file → POST preview → table with per-row valid/error badges → "Import N valid rows" → commit → success toast + invalidate parking queries.
- Cash deposit + petty cash export also reachable from their pages if they exist (check `frontend/src/app`).

## Verify

- Backend: `./mvnw -q compile` + new tests in `ParkingFlowIntegrationTest` (export returns xlsx content-type + non-empty body; import preview detects layout; commit inserts rows; invalid row flagged).
- Frontend: `npm run lint`, `npm run build`.
- Commit + push `main` (follows `7a7a6e4`).

## Batches (tiny, 1–3 files)
1. pom.xml + plan
2. ExcelExportService (daybook, statement, bookings)
3. ExcelExportService (cash-deposit, petty-cash) + repo method
4. ExcelController (5 GET endpoints)
5. ExcelImportService (parse+validate+commit)
6. ImportController + preview/commit DTOs
7. Backend tests
8. api.ts downloadFile + types
9. Parking page export toolbar + import modal
10. lint + build + commit + push
