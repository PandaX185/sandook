# Plan — feat-excel-consolidation

**Date:** 2026-08-10 · **Repo:** `/data/Projects/sandook` (main)
**Source:** follow-up to `plan/feat-excel-import-export.md` (phase 4, shipped in `febf26b`).
**Agreed direction (locked in earlier session):** ONE workbook with 5 clean sheets, no title/total rows,
UPPER-CASE headers, `D/M/YYYY` dates, fuzzy per-sheet detection on import (skip unknown sheets),
single top-bar Export + Import. All locked details below.

## Locked format (from earlier agreement)

Single workbook, sheet order + headers exactly:

1. **Day Book** — `DATE | CAR NUMBER | DURATION | TERM | CASH | CARD | PAYMENT STATUS`
   - bills: CASH→CASH col, CARD→CARD col, DURATION/TERM empty, PAYMENT STATUS `P` (every bill is paid)
2. **Cash Statement** — `DATE | AMOUNT | REMARKS | BALANCE`
   - cash moves date-asc; signed amount (OPENING +, others −); remarks = description or type label; running balance (opening row naturally first)
3. **Bookings** — `SL.NO | CAR PLATE | VALID FROM | VALID TO | TOTAL PRICE | MONTHLY AMOUNT | TERM | PAYMENT STATUS`
   - SL.NO 1..n; VALID FROM = nextDueDate; VALID TO = paidThroughDate (blank if never paid); TOTAL PRICE = monthly × interval months; TERM = `MONTHLY` / `3 MONTHS` / `6 MONTHS` / `CUSTOM (N MONTHS)`; PAYMENT STATUS = computed status
4. **Cash Deposit** — `DATE | SALES AMOUNT | EXTRA | WITHDRAW | NET CASH | DEPOSIT AMOUNT | BALANCE | REMARKS | REFERENCE | NOTES`
   - NET CASH = sales + extra − withdraw; BALANCE = running; REMARKS = deposit remarks; REFERENCE = ref
5. **Petty Cash** — `DATE | AMOUNT | REMARKS | BALANCE`
   - PUT positive / TAKE negative, running balance

Style: one header row (grey fill, bold white — reuse existing style), `d/m/yyyy` date format (was `dd/mm/yyyy`),
`#,##0.00` money, auto-size columns, no merged cells, no totals. Max 10 columns (limit 12). ✓

## Backend

### A. `ExcelExportService` — replace 5 workbooks with one `exportAll(bookId)`
- One `XSSFWorkbook`, 5 sheets named exactly as above (note: "Cash Statement" and "Bookings" — not the old "cash statement"/"BOOKING SHEET"; no per-month sheets anymore).
- Full book, no `from/to/year` params (single global Export; note: old parking-toolbar export used date filters — decision: drop them, export is whole-book).
- Keep helpers (`writeHeader`, `moneyStyle`, `autoSize`, `toBytes`); change `DATE_FMT` → `d/m/yyyy`; remove `groupByMonth`/`dateOf`/`MONTH_FMT`.
- Data: `billService.list(bookId, null, null, null, null)`, `cashMoveService.list(bookId, null, null)`, `bookingService.list(bookId, null, null)`, `cashDayService.list(bookId)`, `pettyCashService.list(bookId)` — confirm each is date-asc (spot-check during implementation; order by date/id if needed).

### B. `ExcelController` — one endpoint instead of 5
- `GET /api/v1/books/{bookId}/exports/all` → `exportAll(bookId)`, filename `sandook_ledger.xlsx`. Delete the 5 old GETs.

### C. `ExcelImportService` — per-sheet fuzzy detection
- **Canonical header aliases** (normalize = lowercase, strip punctuation, collapse spaces) so BOTH the 3 original files AND the new consolidated workbook import:
  - `date`, `amount`, `duration`, `cash`, `card`, `notes`, `withdraw`
  - `carNumber` ← "car number"; `plateNo` ← "car plate number", "car plate"
  - `validFrom` ← "due date from", "valid from"; `validTo` ← "due date to", "valid to"
  - `monthlyAmount` ← "monthly amount"; `totalPrice` ← "total price"
  - `term` ← "term", "term (duration of rent)"; `paymentStatus` ← "payment status"; `slNo` ← "sl.no", "sl no"
  - `salesAmount` ← "sales amount"; `extra` ← "extra amount take fr", "extra"
  - `depositAmount` ← "deposit amount"; `netCash` ← "net cash" (ignored on import); `balance` (ignored)
  - `remarks` ← "remarks", "deposit remarks"; `reference` ← "reference/receipt no", "reference", "receipt no"
- **Per-sheet layout detection** (`detectSheetLayout(sheet)` → layout or null):
  1. sheet-name fuzzy match (normalized name contains `daybook`/`statement`/`booking`/`deposit`/`petty`)
  2. header canonical match (same rule as today's `detectLayout`, but on aliases)
  3. null → sheet **skipped** (recorded), not a hard error
- **DTOs:**
  - `ImportPreviewRow` + `ImportLayout layout` (per-row layout)
  - `ImportPreviewResponse` → `(String fileName, List<ImportPreviewRow> rows, List<String> skippedSheets)` — drop top-level `layout`
  - `ImportCommitRequest` → `(List<ImportPreviewRow> rows)` — drop top-level `layout`; commit validates + inserts each row against its own layout (re-validate defensively, keep CASH_DEPOSIT date dedupe set)
- Field extractors (`dayBookFields`, `bookingFields`, `statementFields`, `cashDepositFields`, `pettyCashFields`) switch to canonical keys; `parseTerm` also accepts `THREE_MONTHS`/`SIX_MONTHS` (export writes `3 MONTHS`/`6 MONTHS`, both accepted).
- Preview of a file with NO recognizable sheets → `BadRequestException("no recognized sheets")`.

### D. Tests (`ParkingFlowIntegrationTest`)
- `exportAll` returns xlsx content-type + workbook with the 5 sheet names in order + header rows.
- Import: multi-sheet file (Day Book + statement in one workbook) → per-row layouts; unknown sheet appears in `skippedSheets`; commit of mixed-layout rows inserts into the right tables.
- Round-trip: `exportAll` bytes → `preview` → all rows valid → `commit` → counts match.

## Frontend

### E. `types.ts`
- `ImportPreviewRow` + `layout: string`; `ImportPreviewResponse` → `{ fileName, rows, skippedSheets: string[] }`.

### F. `ImportExcelDialog` — move to `components/` + multi-layout preview
- Move `app/parking/ImportExcelDialog.tsx` → `components/ImportExcelDialog.tsx` (shared; update Parking.tsx import).
- Preview: layout badge per sheet (group rows by `sheet` + show its layout), skipped-sheets notice, keep row valid/error table.
- Commit: `POST { rows: validRows }` (no top-level layout).

### G. `AppShell` — single top-bar Import/Export
- Export button (both roles) → `downloadFile('/api/v1/books/${bookId}/exports/all', 'sandook_ledger.xlsx')` via `useBook().selectedBookId`; disabled when no book.
- Import button (EDITOR only) → opens shared `ImportExcelDialog`.
- Parking.tsx: remove the 5 export buttons + local Import button (toolbar reverts to filters only).

### H. i18n
- Reuse `common.import` / `common.export` for the top bar; add `imports.skippedSheets`; remove now-unused `parking.export.*` keys (en.json + ar.json).

## Verify
- Backend: `./mvnw -q compile` + `make backend-test` (Testcontainers, needs Docker) — all green.
- Frontend: `npm run lint`, `npm run build`.
- Commit + push `main`.

## Batches (tiny, 1–3 files each)
1. `ExcelExportService` rewrite (exportAll) + plan file
2. `ExcelController` (exports/all) + `ExcelImportService` DTOs (`ImportLayout` on row, response/request records)
3. `ExcelImportService` detection + extractors (canonical aliases, per-sheet, skippedSheets)
4. Backend tests
5. `types.ts` + move `ImportExcelDialog` to components + dialog update
6. `AppShell` top-bar buttons + Parking.tsx cleanup
7. i18n keys + lint + build + commit + push
