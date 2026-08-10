# Phase 4 (Excel Import/Export) — Remaining Changes & Needed Fixes

**Date:** 2026-08-10 · Repo: `/data/Projects/sandook` (main) · Base: `7a7a6e4`
**Status snapshot:** export side done; import service/controller/DTOs written but **do not compile** (3 errors); no backend tests; no frontend work.

---

## 1. Current State (verified)

### Done (untracked in git)
- `backend/pom.xml` — `org.apache.poi:poi-ooxml:5.3.0` added (modified)
- `common/BadRequestException.java` (new) + handler in `common/ApiExceptionHandler.java` (modified)
- `excel/ExcelExportService.java` — 5 workbooks, layouts match originals (compiles)
- `excel/ExcelController.java` — 5 GET export endpoints (compiles)
- `excel/ExcelImportService.java` — **767 lines, does NOT compile**
- `excel/ExcelImportController.java` — POST `/preview` + `/commit`, `hasRole('EDITOR')` (compiles)
- `excel/ImportLayout.java`, `ImportPreviewRow.java`, `ImportPreviewResponse.java`, `ImportCommitRequest.java`, `ImportCommitResponse.java` (compile)
- `plan/feat-excel-import-export.md` (untracked)

### Not started
- Backend tests (0 excel/import/export references in `ParkingFlowIntegrationTest`)
- Frontend (`api.ts` has no `downloadFile`; `types.ts` has no import types; parking page has no export/import UI)
- Verify (compile/test/lint/build), commit, push

---

## 2. Backend — Compile Fixes (blocking, `mvnw compile` currently fails)

All in `backend/src/main/java/com/sandook/ledger/excel/ExcelImportService.java`:

### Fix 1 — `bookId` not in scope (line ~210)
`parseSheet(...)` calls `validate(layout, fields, bookId)` but has no `bookId` parameter.

```java
// signature change
private void parseSheet(Sheet sheet, ImportLayout layout, Map<String, Integer> headers,
                        Long bookId, List<ImportPreviewRow> rows) {
```
Caller in `preview()`:
```java
parseSheet(sheet, layout, headers, bookId, rows);
```

### Fix 2 — `money(Row, Integer)` vs `money(Cell)` (lines ~221–222 in `dayBookFields`)
```java
Long cash = money(row.getCell(h.get("cash")));
Long card = money(row.getCell(h.get("card")));
```

### Fix 3 — `date(Row, Integer)` vs `date(Cell)` (line ~242 in `bookingFields`)
```java
LocalDate paidThrough = date(row.getCell(h.get("due date to")));
```

After these three, `./mvnw -q compile` must pass before anything else.

---

## 3. Backend — Logic Fixes (found during review; do these before tests)

### 3.1 Day Book: payment-status check must survive commit re-validation
`dayBookFields` throws `RowError` for non-"P" status **at parse time only**. The fields map doesn't carry the status, so a crafted `POST /imports/commit` JSON could insert a row with any status. Per design ("commit re-validates every row — client could send arbitrary JSON"):

- In `dayBookFields`: replace the throw with storing the normalized value:
  ```java
  String status = normalize(text(row.getCell(h.get("payment status"))));
  f.put("paymentStatus", status);
  ```
- In `validate(DAY_BOOK)`: add
  ```java
  String st = str(f, "paymentStatus");
  if (st != null && !st.isEmpty() && !st.equalsIgnoreCase("p")) {
      errors.add("payment status is not P — row skipped");
  }
  ```

### 3.2 Booking: missing `paidThroughDate >= nextDueDate` check
Design said TO must be ≥ FROM; not implemented. Add to `validate(BOOKING_SHEET)`:
```java
LocalDate from = f.get("nextDueDate") instanceof String s ? LocalDate.parse(s) : null;
LocalDate to = f.get("paidThroughDate") instanceof String s2 ? LocalDate.parse(s2) : null;
if (from != null && to != null && to.isBefore(from)) {
    errors.add("paidThroughDate must be on or after nextDueDate");
}
```

### 3.3 Cash Deposit: in-batch duplicate-date dedupe (currently a data-loss bug)
`validate(CASH_DEPOSIT)` only checks `cashDayRepository.existsByBookIdAndDate`. If the same file has two rows for one date, both pass → first insert succeeds, second violates the `UNIQUE(book_id, date)` constraint → **whole transaction rolls back, nothing imported**.

Fix in `commit()` (authoritative):
```java
Set<LocalDate> seenCashDates = new HashSet<>();
// inside the row loop, before insert:
if (request.layout() == ImportLayout.CASH_DEPOSIT) {
    LocalDate d = ...date from fields...;
    if (!seenCashDates.add(d)) {
        skipped++;
        continue;
    }
}
```
Nice-to-have: same dedupe in `preview` so rows show as invalid before commit.

### 3.4 `commit()` null-safety
`ImportCommitRequest` can arrive with `null` layout/rows (or body `null`) → NPE/500. Guard at the top:
```java
if (request == null || request.layout() == null || request.rows() == null) {
    throw new BadRequestException("Import commit body must include layout and rows");
}
```

### 3.5 Minor / optional
- `parseTerm` regex only accepts `CUSTOM (N MONTHS)` with parens (export writes parens form, so round-trip is fine). Optional: also accept bare `CUSTOM N MONTHS`.
- `insert(PETTY_CASH)` calls `requireBook(bookId)` per row (N+1). Harmless; could resolve the book once in `commit()` and pass it.
- `preview` wraps *everything* (incl. unexpected bugs) in `BadRequestException`. Acceptable, but log the cause to make real failures debuggable.

---

## 4. Backend — Tests to Add

File: `backend/src/test/java/com/sandook/ledger/ParkingFlowIntegrationTest.java`
Pattern already used there: `String token = login("editor");` then `mockMvc.perform(...).header("Authorization", "Bearer " + token)`.

Add a POI helper (in the test class or a small `ExcelTestFixtures` class):
```java
static byte[] workbookBytes(Consumer<XSSFWorkbook> fill) throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        fill.accept(wb);
        wb.write(out);
        return out.toByteArray();
    }
}
```
Import `org.apache.poi.xssf.usermodel.*` — already on the classpath via the new dependency.

### Tests
1. **Exports (loop all 5):** GET each endpoint → 200, content-type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition` attachment, non-empty body. Endpoints: `exports/daybook?from&to`, `exports/statement?from&to`, `exports/bookings`, `exports/cash-deposit?year`, `exports/petty-cash?year` under `/api/v1/books/{bookId}/`.
2. **Preview detects layout:** day-book fixture (headers `DATE | CAR NUMBER | DURATION | TERM | CASH | CARD | PAYMENT STATUS`, 2 valid rows) → 200, `layout == DAY_BOOK`, rows valid, fields contain ISO date + `amountMinor` as Long.
3. **Preview flags invalid rows:** one row with both CASH+CARD filled, one with status ≠ P, one missing plate → those rows `valid=false` with non-empty `errors`; valid rows unaffected.
4. **Commit inserts + skips:** commit `{layout: DAY_BOOK, rows: [valid, invalid]}` → `inserted=1, skipped=1`; `parkingBillRepository` count grew by 1; fields persisted (plate, amountMinor, method, billedAt, enteredBy).
5. **Cash deposit duplicate date:** two rows same date → after fix 3.3, first inserted, second skipped (`inserted=1, skipped=1`), no rollback.
6. **Booking round-trip:** `exports/bookings` bytes → preview → `layout == BOOKING_SHEET` → commit → new `parking_booking` with parsed `intervalType`/`intervalMonths`/`active`.
7. **Viewer gets 403:** `login("viewer")` → POST `imports/preview` (MockMultipartFile, field `"file"`) and POST `imports/commit` → 403 both.

Run: `make backend-test` (Testcontainers — Docker must be running; disabled-without-docker is already configured).

---

## 5. Frontend — Changes

### 5.1 `frontend/src/lib/api.ts` — add `downloadFile`
`api()` is JSON-only; exports need a blob fetch. Mirror the existing auth/token handling (Bearer header + `refreshTokens()` on 401, or reuse the same fetch wrapper):
```ts
export async function downloadFile(path: string, filename: string): Promise<void> {
  // fetch with accessToken, on 401 try refresh once
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
```

### 5.2 `frontend/src/lib/types.ts`
```ts
export interface ImportPreviewRow { rowNo: number; sheet: string; fields: Record<string, unknown>; valid: boolean; errors: string[]; }
export interface ImportPreviewResponse { layout: string; fileName: string; rows: ImportPreviewRow[]; }
export interface ImportCommitResponse { inserted: number; skipped: number; }
```

### 5.3 Parking page (`frontend/src/app/parking/Parking.tsx`)
- Toolbar row with 5 export buttons → `downloadFile('/api/v1/books/' + bookId + '/exports/...', 'parking_daybook.xlsx')` etc. Pass `from`/`to` (daybook, statement) and `year` (cash-deposit, petty-cash) from current filters when present.
- "Import Excel" button → modal:
  1. file input (`accept=".xlsx"`) → `POST imports/preview` (multipart, field `"file"`)
  2. render rows as a table with valid/error badges (`valid ? "✓" : "✗"`, errors listed)
  3. "Import N valid rows" → `POST imports/commit` with `{layout, rows: rows.filter(r => r.valid)}` → toast `inserted`/`skipped` → close modal, invalidate parking queries.
- Cash deposit / petty-cash export buttons also belong on their pages (`src/app/cash`, `src/app/petty-cash`) if present — check at implementation time.

---

## 6. Verification & Ship

1. `./mvnw -q compile` (backend compiles after §2)
2. `make backend-test` (all tests incl. new §4)
3. `make frontend-lint` + `make frontend-build`
4. Commit — include the untracked `excel/` package, `common/BadRequestException.java`, modified `pom.xml`/`ApiExceptionHandler.java`, **and both plan files** (`feat-excel-import-export.md`, this doc). Conventional message, e.g.:
   - `feat(excel): export 5 parking workbooks`
   - `feat(excel): import preview and commit`
5. Push `main`.

---

## Checklist

- [ ] §2 compile fixes (3)
- [ ] §3.1 day-book payment status in validate
- [ ] §3.2 booking FROM ≤ TO check
- [ ] §3.3 cash-deposit in-batch dedupe (commit + preview)
- [ ] §3.4 commit null-safety
- [ ] §4 backend tests (7 groups)
- [ ] §5 frontend (api.ts, types.ts, parking toolbar + modal)
- [ ] §6 lint, build, commit, push
