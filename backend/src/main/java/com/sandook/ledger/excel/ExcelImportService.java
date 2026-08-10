package com.sandook.ledger.excel;

import com.sandook.ledger.audit.AuditService;
import com.sandook.ledger.book.Book;
import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.cash.CashDay;
import com.sandook.ledger.cash.CashDayRepository;
import com.sandook.ledger.common.BadRequestException;
import com.sandook.ledger.common.NotFoundException;
import com.sandook.ledger.parking.ParkingBill;
import com.sandook.ledger.parking.ParkingBillRepository;
import com.sandook.ledger.parking.ParkingBooking;
import com.sandook.ledger.parking.ParkingBookingInterval;
import com.sandook.ledger.parking.ParkingBookingRepository;
import com.sandook.ledger.parking.ParkingCashMove;
import com.sandook.ledger.parking.ParkingCashMoveRepository;
import com.sandook.ledger.parking.ParkingCashMoveType;
import com.sandook.ledger.parking.PaymentMethod;
import com.sandook.ledger.pettycash.PettyCashTransaction;
import com.sandook.ledger.pettycash.PettyCashTransactionRepository;
import com.sandook.ledger.pettycash.PettyCashType;
import com.sandook.ledger.user.User;
import com.sandook.ledger.user.UserRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preview-then-commit Excel import with per-sheet layout detection.
 * Supports both the 3 original single-layout files and the consolidated
 * 5-sheet workbook. Unknown sheets are skipped (not rejected).
 * Preview parses + validates every row and writes NOTHING; commit inserts
 * the valid rows transactionally, re-validating each one defensively.
 * Money is parsed with BigDecimal (never double) and stored in minor units.
 */
@Service
public class ExcelImportService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ExcelImportService.class);

    private static final Pattern CUSTOM_TERM = Pattern.compile(
            "CUSTOM\\s*(?:\\(?\\s*(\\d{1,2})\\s*MONTHS?\\)?)?", Pattern.CASE_INSENSITIVE);
    private static final int MAX_PLATE = 20;
    private static final int MAX_TEXT = 255;
    private static final int MAX_REF = 100;

    // --- Header alias map (canonical key → list of accepted header spellings) ---

    private static final Map<String, List<String>> HEADER_ALIASES = Map.ofEntries(
            Map.entry("date", List.of("date")),
            Map.entry("amount", List.of("amount")),
            Map.entry("duration", List.of("duration")),
            Map.entry("cash", List.of("cash")),
            Map.entry("card", List.of("card")),
            Map.entry("notes", List.of("notes")),
            Map.entry("withdraw", List.of("withdraw")),
            Map.entry("carNumber", List.of("car number", "car no")),
            Map.entry("plateNo", List.of("car plate number", "car plate", "plate no", "plate")),
            Map.entry("validFrom", List.of("due date from", "valid from", "due from")),
            Map.entry("validTo", List.of("due date to", "valid to", "due to")),
            Map.entry("monthlyAmount", List.of("monthly amount")),
            Map.entry("totalPrice", List.of("total price")),
            Map.entry("term", List.of("term", "term duration of rent")),
            Map.entry("paymentStatus", List.of("payment status", "status")),
            Map.entry("slNo", List.of("sl no", "serial no", "sl")),
            Map.entry("salesAmount", List.of("sales amount")),
            Map.entry("extra", List.of("extra amount take fr", "extra")),
            Map.entry("depositAmount", List.of("deposit amount")),
            Map.entry("netCash", List.of("net cash")),
            Map.entry("balance", List.of("balance")),
            Map.entry("remarks", List.of("remarks", "deposit remarks", "remark")),
            Map.entry("reference", List.of("reference receipt no", "reference", "receipt no", "receipt"))
    );

    /** Pre-computed reverse map: normalised alias → canonical key. */
    private static final Map<String, String> CANONICAL_BY_ALIAS = buildAliasMap();

    private static Map<String, String> buildAliasMap() {
        Map<String, String> m = new HashMap<>();
        for (Map.Entry<String, List<String>> e : HEADER_ALIASES.entrySet()) {
            for (String alias : e.getValue()) {
                m.put(keyOf(alias), e.getKey());
            }
        }
        return m;
    }

    // --- Repositories / services ---------------------------------------------

    private final ParkingBillRepository billRepository;
    private final ParkingBookingRepository bookingRepository;
    private final ParkingCashMoveRepository moveRepository;
    private final CashDayRepository cashDayRepository;
    private final PettyCashTransactionRepository pettyCashRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ExcelImportService(ParkingBillRepository billRepository,
                              ParkingBookingRepository bookingRepository,
                              ParkingCashMoveRepository moveRepository,
                              CashDayRepository cashDayRepository,
                              PettyCashTransactionRepository pettyCashRepository,
                              BookRepository bookRepository,
                              UserRepository userRepository,
                              AuditService auditService) {
        this.billRepository = billRepository;
        this.bookingRepository = bookingRepository;
        this.moveRepository = moveRepository;
        this.cashDayRepository = cashDayRepository;
        this.pettyCashRepository = pettyCashRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    // --- Preview (per-sheet detection) ----------------------------------------

    public ImportPreviewResponse preview(Long bookId, MultipartFile file) {
        requireBook(bookId);
        String name = file.getOriginalFilename();
        if (name != null && !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BadRequestException("Only .xlsx files are supported");
        }
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            List<ImportPreviewRow> rows = new ArrayList<>();
            List<String> skippedSheets = new ArrayList<>();
            Set<LocalDate> seenCashDates = new HashSet<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                if (sheet.getLastRowNum() < 1) {
                    continue; // header-only or empty
                }
                ImportLayout layout = detectSheetLayout(sheet);
                if (layout == null) {
                    skippedSheets.add(sheet.getSheetName());
                    continue;
                }
                Map<String, Integer> headers = canonicalHeaderMap(sheet);
                List<String> missing = missingColumns(layout, headers);
                if (!missing.isEmpty()) {
                    rows.add(new ImportPreviewRow(0, sheet.getSheetName(), layout, Map.of(), false,
                            List.of("Sheet headers do not match " + layout + " — missing: "
                                    + String.join(", ", missing))));
                    continue;
                }
                parseSheet(sheet, layout, headers, bookId, seenCashDates, rows);
            }
            if (rows.isEmpty() && skippedSheets.isEmpty()) {
                throw new BadRequestException("No data rows found in the workbook");
            }
            if (rows.isEmpty()) {
                throw new BadRequestException("No recognized sheets in the workbook — found: "
                        + String.join(", ", skippedSheets));
            }
            return new ImportPreviewResponse(name, rows, skippedSheets);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel preview failed for book {}", bookId, e);
            throw new BadRequestException("Could not read Excel file: " + e.getMessage());
        }
    }

    // --- Per-sheet layout detection -------------------------------------------

    private ImportLayout detectSheetLayout(Sheet sheet) {
        String name = sheetNameKey(sheet.getSheetName());
        // 1) sheet-name fuzzy match
        if (name.contains("daybook")) return ImportLayout.DAY_BOOK;
        if (name.contains("petty")) return ImportLayout.PETTY_CASH;
        if (name.contains("deposit")) return ImportLayout.CASH_DEPOSIT;
        if (name.contains("booking")) return ImportLayout.BOOKING_SHEET;
        if (name.contains("statement")) return ImportLayout.CASH_STATEMENT;
        // 2) header canonical match (fallback for month-named sheets like "Sep 2025")
        Map<String, Integer> headers = canonicalHeaderMap(sheet);
        if (headers.containsKey("carNumber") && headers.containsKey("paymentStatus")) {
            return ImportLayout.DAY_BOOK;
        }
        if (headers.containsKey("plateNo") && headers.containsKey("validFrom")) {
            return ImportLayout.BOOKING_SHEET;
        }
        if (headers.containsKey("salesAmount") && headers.containsKey("depositAmount")) {
            return ImportLayout.CASH_DEPOSIT;
        }
        if (headers.containsKey("amount") && headers.containsKey("remarks") && headers.containsKey("balance")) {
            // original "cash statement" vs month-named petty: name didn't match keywords above,
            // so this is a month-named petty cash sheet
            return ImportLayout.PETTY_CASH;
        }
        // 3) unrecognized
        return null;
    }

    /** Lowercase + strip all non-alphanumerics (keep letters/digits only). */
    private String sheetNameKey(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    /** Canonical header map from the first row. Only keys in HEADER_ALIASES are kept. */
    private Map<String, Integer> canonicalHeaderMap(Sheet sheet) {
        Map<String, Integer> map = new LinkedHashMap<>();
        Row row = sheet.getRow(0);
        if (row == null) {
            return map;
        }
        for (Cell cell : row) {
            String key = CANONICAL_BY_ALIAS.get(keyOf(text(cell)));
            if (key != null && !map.containsKey(key)) {
                map.put(key, cell.getColumnIndex());
            }
        }
        return map;
    }

    /** Required canonical columns per layout (others are optional / guarded). */
    private List<String> missingColumns(ImportLayout layout, Map<String, Integer> headers) {
        List<String> required = switch (layout) {
            case DAY_BOOK -> List.of("date", "carNumber");
            case BOOKING_SHEET -> List.of("plateNo", "validFrom", "monthlyAmount");
            case CASH_STATEMENT -> List.of("date", "amount");
            case CASH_DEPOSIT -> List.of("date", "salesAmount", "depositAmount");
            case PETTY_CASH -> List.of("date", "amount", "remarks");
        };
        return required.stream().filter(c -> !headers.containsKey(c)).toList();
    }

    private void parseSheet(Sheet sheet, ImportLayout layout, Map<String, Integer> headers,
                            Long bookId, Set<LocalDate> seenCashDates, List<ImportPreviewRow> rows) {
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, Object> fields;
            List<String> errors;
            try {
                fields = switch (layout) {
                    case DAY_BOOK -> dayBookFields(row, headers);
                    case BOOKING_SHEET -> bookingFields(row, headers);
                    case CASH_STATEMENT -> statementFields(row, headers);
                    case CASH_DEPOSIT -> cashDepositFields(row, headers);
                    case PETTY_CASH -> pettyCashFields(row, headers);
                };
            } catch (RowError e) {
                rows.add(new ImportPreviewRow(r + 1, sheet.getSheetName(), layout, Map.of(), false,
                        List.of(e.getMessage())));
                continue;
            }
            errors = validate(layout, fields, bookId);
            if (layout == ImportLayout.CASH_DEPOSIT && errors.isEmpty()) {
                LocalDate d = date(fields, "date");
                if (!seenCashDates.add(d)) {
                    errors.add("cash day already exists for " + d);
                }
            }
            rows.add(new ImportPreviewRow(r + 1, sheet.getSheetName(), layout, fields, errors.isEmpty(), errors));
        }
    }

    // --- Field extraction (canonical keys) ------------------------------------

    private Map<String, Object> dayBookFields(Row row, Map<String, Integer> h) throws RowError {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("date", iso(requiredDate(row, h.get("date"), "DATE")));
        f.put("plateNo", requiredText(row, h.get("carNumber"), "CAR NUMBER", MAX_PLATE));
        Integer cIdx = h.get("cash");
        Integer cardIdx = h.get("card");
        Long cash = cIdx == null ? null : money(row.getCell(cIdx));
        Long card = cardIdx == null ? null : money(row.getCell(cardIdx));
        if (cash != null && card != null) {
            throw new RowError("both CASH and CARD columns are filled — use one");
        }
        if (cash == null && card == null) {
            throw new RowError("no amount in CASH or CARD column");
        }
        f.put("amountMinor", cash != null ? cash : card);
        f.put("paymentMethod", cash != null ? "CASH" : "CARD");
        Integer psIdx = h.get("paymentStatus");
        f.put("paymentStatus", psIdx == null ? "" : normalize(text(row.getCell(psIdx))));
        return f;
    }

    private Map<String, Object> bookingFields(Row row, Map<String, Integer> h) throws RowError {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("plateNo", requiredText(row, h.get("plateNo"), "Car Plate Number", MAX_PLATE));
        f.put("nextDueDate", iso(requiredDate(row, h.get("validFrom"), "Due date FROM")));
        Integer vtIdx = h.get("validTo");
        LocalDate paidThrough = vtIdx == null ? null : date(row.getCell(vtIdx));
        f.put("paidThroughDate", paidThrough == null ? null : iso(paidThrough));
        Long monthly = requiredMoney(row, h.get("monthlyAmount"), "monthly amount");
        if (monthly < 0) {
            throw new RowError("monthly amount must not be negative");
        }
        f.put("monthlyRateMinor", monthly);
        Integer tmIdx = h.get("term");
        String term = tmIdx == null ? "" : normalize(text(row.getCell(tmIdx)));
        f.put("intervalType", parseTerm(term, f));
        Integer stIdx = h.get("paymentStatus");
        String status = stIdx == null ? "" : normalize(text(row.getCell(stIdx)));
        if (status.isEmpty()) {
            f.put("active", true);
        } else if (status.equalsIgnoreCase("INACTIVE")) {
            f.put("active", false);
        } else if (status.equalsIgnoreCase("PAID") || status.equalsIgnoreCase("DUE")
                || status.equalsIgnoreCase("OVERDUE")) {
            f.put("active", true);
        } else {
            throw new RowError("unknown Payment status: " + status);
        }
        return f;
    }

    private Map<String, Object> statementFields(Row row, Map<String, Integer> h) throws RowError {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("date", iso(requiredDate(row, h.get("date"), "Date")));
        Long amount = requiredMoney(row, h.get("amount"), "Amount");
        if (amount == 0) {
            throw new RowError("Amount must not be zero");
        }
        Integer rmIdx = h.get("remarks");
        String remarks = rmIdx == null ? null : text(row.getCell(rmIdx));
        f.put("description", remarks == null ? null : remarks.trim());
        String type = moveType(remarks);
        boolean inflow = type.equals("OPENING");
        if (inflow && amount < 0) {
            throw new RowError("OPENING amount must be positive");
        }
        if (!inflow && amount > 0) {
            throw new RowError(type + " amount must be negative (cash out)");
        }
        if ((type.equals("EXPENSE") || type.equals("SALARY")) && (remarks == null || remarks.isBlank())) {
            throw new RowError(type + " rows need a remark describing the expense");
        }
        f.put("type", type);
        f.put("amountMinor", Math.abs(amount));
        return f;
    }

    private Map<String, Object> cashDepositFields(Row row, Map<String, Integer> h) throws RowError {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("date", iso(requiredDate(row, h.get("date"), "Date")));
        f.put("salesMinor", nonNegative(row, h.get("salesAmount"), "Sales Amount"));
        Integer exIdx = h.get("extra");
        f.put("extraMinor", exIdx == null ? 0L : nonNegative(row, exIdx, "Extra Amount"));
        Integer wIdx = h.get("withdraw");
        f.put("withdrawMinor", wIdx == null ? 0L : nonNegative(row, wIdx, "Withdraw"));
        f.put("depositMinor", nonNegative(row, h.get("depositAmount"), "Deposit Amount"));
        Integer rmIdx = h.get("remarks");
        f.put("depositRemarks", rmIdx == null ? null : capped(row, rmIdx, "Remarks", MAX_TEXT));
        Integer refIdx = h.get("reference");
        f.put("ref", refIdx == null ? null : capped(row, refIdx, "Reference", MAX_REF));
        Integer notesIdx = h.get("notes");
        String notes = notesIdx == null ? null : text(row.getCell(notesIdx));
        f.put("notes", notes == null ? null : notes.trim());
        return f;
    }

    private Map<String, Object> pettyCashFields(Row row, Map<String, Integer> h) throws RowError {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("date", iso(requiredDate(row, h.get("date"), "Date")));
        Long amount = requiredMoney(row, h.get("amount"), "Amount");
        if (amount == 0) {
            throw new RowError("Amount must not be zero");
        }
        String remarks = requiredText(row, h.get("remarks"), "Remarks", MAX_TEXT);
        f.put("description", remarks);
        f.put("type", amount > 0 ? "PUT" : "TAKE");
        f.put("amountMinor", Math.abs(amount));
        return f;
    }

    // --- Term parsing --------------------------------------------------------

    private String parseTerm(String term, Map<String, Object> f) throws RowError {
        if (term.isEmpty()) {
            return "MONTHLY";
        }
        String upper = term.toUpperCase(Locale.ROOT);
        if (upper.equals("MONTHLY")) {
            return "MONTHLY";
        }
        if (upper.equals("3 MONTHS")) {
            return "THREE_MONTHS";
        }
        if (upper.equals("6 MONTHS")) {
            return "SIX_MONTHS";
        }
        if (upper.equals("THREE_MONTHS")) {
            return "THREE_MONTHS";
        }
        if (upper.equals("SIX_MONTHS")) {
            return "SIX_MONTHS";
        }
        Matcher m = CUSTOM_TERM.matcher(upper);
        if (m.matches()) {
            String months = m.group(1);
            if (months == null) {
                throw new RowError("CUSTOM term needs the month count, e.g. CUSTOM (6 MONTHS) or CUSTOM 6 MONTHS");
            }
            int n = Integer.parseInt(months);
            if (n < 1 || n > 24) {
                throw new RowError("CUSTOM months must be 1-24, got " + n);
            }
            f.put("intervalMonths", n);
            return "CUSTOM";
        }
        throw new RowError("unknown Term: " + term);
    }

    // --- Statement move-type inference ----------------------------------------

    private String moveType(String remarks) {
        if (remarks == null) {
            return "EXPENSE";
        }
        String upper = remarks.toUpperCase(Locale.ROOT);
        if (upper.contains("OPENING")) {
            return "OPENING";
        }
        if (upper.contains("TRANSFER")) {
            return "TRANSFER_TO_SHOP";
        }
        if (upper.contains("SALARY")) {
            return "SALARY";
        }
        if (upper.contains("CLOSING")) {
            return "CLOSING";
        }
        return "EXPENSE";
    }

    // --- Validation (typed map -> errors) ------------------------------------

    private List<String> validate(ImportLayout layout, Map<String, Object> f, Long bookId) {
        List<String> errors = new ArrayList<>();
        switch (layout) {
            case DAY_BOOK -> {
                requireDate(f, "date", errors);
                requireText(f, "plateNo", errors, MAX_PLATE);
                requireMinor(f, "amountMinor", errors);
                requirePositive(f, "amountMinor", errors);
                requireOneOf(f, "paymentMethod", errors, "CASH", "CARD");
                String st = str(f, "paymentStatus");
                if (st != null && !st.isEmpty() && !st.equalsIgnoreCase("p")) {
                    errors.add("payment status is not P — row skipped");
                }
            }
            case BOOKING_SHEET -> {
                requireText(f, "plateNo", errors, MAX_PLATE);
                requireDate(f, "nextDueDate", errors);
                if (f.get("paidThroughDate") != null) {
                    requireDate(f, "paidThroughDate", errors);
                }
                requireMinor(f, "monthlyRateMinor", errors);
                requireOneOf(f, "intervalType", errors,
                        "MONTHLY", "THREE_MONTHS", "SIX_MONTHS", "CUSTOM");
                if ("CUSTOM".equals(str(f, "intervalType")) && intOrNull(f, "intervalMonths") == null) {
                    errors.add("intervalMonths is required for CUSTOM term");
                }
                if (f.get("intervalMonths") != null) {
                    requireInt(f, "intervalMonths", errors);
                }
                LocalDate from = f.get("nextDueDate") instanceof String s ? LocalDate.parse(s) : null;
                LocalDate to = f.get("paidThroughDate") instanceof String s2 ? LocalDate.parse(s2) : null;
                if (from != null && to != null && to.isBefore(from)) {
                    errors.add("paidThroughDate must be on or after nextDueDate");
                }
            }
            case CASH_STATEMENT -> {
                requireDate(f, "date", errors);
                requireMinor(f, "amountMinor", errors);
                requirePositive(f, "amountMinor", errors);
                requireOneOf(f, "type", errors, "OPENING", "TRANSFER_TO_SHOP", "SALARY", "EXPENSE", "CLOSING");
            }
            case CASH_DEPOSIT -> {
                requireDate(f, "date", errors);
                for (String key : List.of("salesMinor", "extraMinor", "withdrawMinor", "depositMinor")) {
                    requireNonNegative(f, key, errors);
                }
                LocalDate d = f.get("date") instanceof String s ? LocalDate.parse(s) : null;
                if (d != null && cashDayRepository.existsByBookIdAndDate(bookId, d)) {
                    errors.add("cash day already exists for " + d);
                }
            }
            case PETTY_CASH -> {
                requireDate(f, "date", errors);
                requireText(f, "description", errors, MAX_TEXT);
                requireMinor(f, "amountMinor", errors);
                requirePositive(f, "amountMinor", errors);
                requireOneOf(f, "type", errors, "PUT", "TAKE");
            }
        }
        return errors;
    }

    // --- Commit (per-row layout) ---------------------------------------------

    @Transactional
    public ImportCommitResponse commit(Long bookId, ImportCommitRequest request, String username) {
        if (request == null || request.rows() == null) {
            throw new BadRequestException("Import commit body must include rows");
        }
        Book book = requireBook(bookId);
        Long enteredBy = userId(username);
        int inserted = 0;
        int skipped = 0;
        Set<LocalDate> seenCashDates = new HashSet<>();
        for (ImportPreviewRow row : request.rows()) {
            if (row == null || row.layout() == null || row.fields() == null) {
                skipped++;
                continue;
            }
            Map<String, Object> f = new LinkedHashMap<>(row.fields());
            List<String> errors = validate(row.layout(), f, bookId);
            if (!errors.isEmpty()) {
                skipped++;
                continue;
            }
            if (row.layout() == ImportLayout.CASH_DEPOSIT) {
                LocalDate d = date(f, "date");
                if (!seenCashDates.add(d)) {
                    skipped++;
                    continue;
                }
            }
            insert(row.layout(), book, f, enteredBy);
            inserted++;
        }
        return new ImportCommitResponse(inserted, skipped);
    }

    private void insert(ImportLayout layout, Book book, Map<String, Object> f, Long enteredBy) {
        Long bookId = book.getId();
        switch (layout) {
            case DAY_BOOK -> {
                ParkingBill bill = new ParkingBill();
                bill.setBookId(bookId);
                bill.setPlateNo(str(f, "plateNo"));
                bill.setAmountMinor(minor(f, "amountMinor"));
                bill.setPaymentMethod(PaymentMethod.valueOf(str(f, "paymentMethod")));
                bill.setBilledAt(date(f, "date"));
                bill.setEnteredBy(enteredBy);
                billRepository.save(bill);
                auditService.record("CREATE", "parking_bill", bill.getId(), null, bill);
            }
            case BOOKING_SHEET -> {
                ParkingBooking booking = new ParkingBooking();
                booking.setBookId(bookId);
                booking.setPlateNo(str(f, "plateNo"));
                booking.setMonthlyRateMinor(minor(f, "monthlyRateMinor"));
                booking.setIntervalType(ParkingBookingInterval.valueOf(str(f, "intervalType")));
                booking.setIntervalMonths(intOrNull(f, "intervalMonths"));
                booking.setNextDueDate(date(f, "nextDueDate"));
                booking.setPaidThroughDate(dateOrNull(f, "paidThroughDate"));
                booking.setActive(Boolean.TRUE.equals(f.get("active")));
                booking.setEnteredBy(enteredBy);
                bookingRepository.save(booking);
                auditService.record("CREATE", "parking_booking", booking.getId(), null, booking);
            }
            case CASH_STATEMENT -> {
                ParkingCashMove move = new ParkingCashMove();
                move.setBookId(bookId);
                move.setDate(date(f, "date"));
                move.setType(ParkingCashMoveType.valueOf(str(f, "type")));
                move.setAmountMinor(minor(f, "amountMinor"));
                move.setDescription(strOrNull(f, "description"));
                move.setEnteredBy(enteredBy);
                moveRepository.save(move);
                auditService.record("CREATE", "parking_cash_move", move.getId(), null, move);
            }
            case CASH_DEPOSIT -> {
                CashDay day = new CashDay();
                day.setBookId(bookId);
                day.setDate(date(f, "date"));
                day.setSalesMinor(minor(f, "salesMinor"));
                day.setExtraMinor(minor(f, "extraMinor"));
                day.setWithdrawMinor(minor(f, "withdrawMinor"));
                day.setDepositMinor(minor(f, "depositMinor"));
                day.setDepositRemarks(strOrNull(f, "depositRemarks"));
                day.setRef(strOrNull(f, "ref"));
                day.setNotes(strOrNull(f, "notes"));
                day.setEnteredBy(enteredBy);
                cashDayRepository.save(day);
                auditService.record("CREATE", "cash_day", day.getId(), null, day);
            }
            case PETTY_CASH -> {
                PettyCashTransaction tx = new PettyCashTransaction();
                tx.setBookId(bookId);
                tx.setDate(date(f, "date"));
                tx.setDescription(str(f, "description"));
                tx.setType(PettyCashType.valueOf(str(f, "type")));
                tx.setAmountMinor(minor(f, "amountMinor"));
                tx.setCurrencyCode(book.getCurrencyCode());
                tx.setEnteredBy(enteredBy);
                pettyCashRepository.save(tx);
                auditService.record("CREATE", "petty_cash_tx", tx.getId(), null, tx);
            }
        }
    }

    // --- Cell helpers --------------------------------------------------------

    private static final DataFormatter FORMATTER = new DataFormatter();

    private String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.FORMULA) {
            return FORMATTER.formatCellValue(cell);
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> FORMATTER.formatCellValue(cell);
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private LocalDate date(Cell cell) throws RowError {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell) && cell.getLocalDateTimeCellValue() != null) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            java.util.Date javaDate = DateUtil.getJavaDate(cell.getNumericCellValue());
            LocalDate d = javaDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            if (d.getYear() < 1900 || d.getYear() > 2200) {
                throw new RowError("invalid date value: " + FORMATTER.formatCellValue(cell));
            }
            return d;
        }
        String value = text(cell);
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseDate(value.trim());
    }

    private LocalDate parseDate(String value) throws RowError {
        try {
            String[] parts = value.split("[/\\\\-]");
            if (parts.length != 3) {
                throw new RowError("invalid date: " + value + " (expected DD/MM/YYYY)");
            }
            int a = Integer.parseInt(parts[0].trim());
            int b = Integer.parseInt(parts[1].trim());
            int y = Integer.parseInt(parts[2].trim());
            if (parts[0].trim().length() == 4) {
                return LocalDate.of(a, b, y);
            }
            return LocalDate.of(y, b, a);
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new RowError("invalid date: " + value + " (expected DD/MM/YYYY)");
        }
    }

    private LocalDate requiredDate(Row row, Integer idx, String label) throws RowError {
        if (idx == null) {
            throw new RowError(label + " is required");
        }
        LocalDate d = date(row.getCell(idx));
        if (d == null) {
            throw new RowError(label + " is required");
        }
        return d;
    }

    private Long money(Cell cell) throws RowError {
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue())
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValue();
            }
            String value = text(cell);
            if (value == null || value.isBlank()) {
                return null;
            }
            String cleaned = value.replace(",", "").trim();
            return new BigDecimal(cleaned)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
        } catch (NumberFormatException e) {
            throw new RowError("invalid amount: " + FORMATTER.formatCellValue(cell));
        }
    }

    private Long requiredMoney(Row row, Integer idx, String label) throws RowError {
        if (idx == null) {
            throw new RowError(label + " is required");
        }
        Long value = money(row.getCell(idx));
        if (value == null) {
            throw new RowError(label + " is required");
        }
        return value;
    }

    private Long nonNegative(Row row, Integer idx, String label) throws RowError {
        if (idx == null) {
            return 0L;
        }
        Long value = money(row.getCell(idx));
        if (value == null) {
            return 0L;
        }
        if (value < 0) {
            throw new RowError(label + " must not be negative");
        }
        return value;
    }

    private String requiredText(Row row, Integer idx, String label, int max) throws RowError {
        if (idx == null) {
            throw new RowError(label + " is required");
        }
        String value = text(row.getCell(idx));
        if (value == null || value.isBlank()) {
            throw new RowError(label + " is required");
        }
        value = value.trim();
        if (value.length() > max) {
            throw new RowError(label + " is too long (max " + max + " chars)");
        }
        return value;
    }

    private String capped(Row row, Integer idx, String label, int max) throws RowError {
        if (idx == null) {
            return null;
        }
        String value = text(row.getCell(idx));
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.trim();
        if (value.length() > max) {
            throw new RowError(label + " is too long (max " + max + " chars)");
        }
        return value;
    }

    // --- Key / value helpers -------------------------------------------------

    /** Normalise a header for alias lookup: lowercase, strip non-alphanumeric, collapse spaces. */
    private static String keyOf(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String iso(LocalDate date) {
        return date.toString();
    }

    // --- Map helpers (commit side; JSON round-trip safe) ---------------------

    private String str(Map<String, Object> f, String key) {
        Object v = f.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private String strOrNull(Map<String, Object> f, String key) {
        String v = str(f, key);
        return v == null || v.isBlank() ? null : v;
    }

    private LocalDate date(Map<String, Object> f, String key) {
        return LocalDate.parse(str(f, key));
    }

    private LocalDate dateOrNull(Map<String, Object> f, String key) {
        String v = str(f, key);
        return v == null || v.isBlank() ? null : LocalDate.parse(v);
    }

    private long minor(Map<String, Object> f, String key) {
        return ((Number) f.get(key)).longValue();
    }

    private Integer intOrNull(Map<String, Object> f, String key) {
        Object v = f.get(key);
        return v == null ? null : ((Number) v).intValue();
    }

    private void requireDate(Map<String, Object> f, String key, List<String> errors) {
        Object v = f.get(key);
        if (v == null || str(f, key).isBlank()) {
            errors.add(key + " is required");
            return;
        }
        try {
            LocalDate.parse(str(f, key));
        } catch (DateTimeParseException e) {
            errors.add("invalid date for " + key + ": " + v);
        }
    }

    private void requireText(Map<String, Object> f, String key, List<String> errors, int max) {
        String v = strOrNull(f, key);
        if (v == null) {
            errors.add(key + " is required");
        } else if (v.length() > max) {
            errors.add(key + " is too long (max " + max + " chars)");
        }
    }

    private void requireMinor(Map<String, Object> f, String key, List<String> errors) {
        Object v = f.get(key);
        if (!(v instanceof Number)) {
            errors.add(key + " is required");
        }
    }

    private void requirePositive(Map<String, Object> f, String key, List<String> errors) {
        Object v = f.get(key);
        if (v instanceof Number n && n.longValue() <= 0) {
            errors.add(key + " must be greater than zero");
        }
    }

    private void requireNonNegative(Map<String, Object> f, String key, List<String> errors) {
        Object v = f.get(key);
        if (v instanceof Number n && n.longValue() < 0) {
            errors.add(key + " must not be negative");
        }
    }

    private void requireInt(Map<String, Object> f, String key, List<String> errors) {
        Object v = f.get(key);
        if (!(v instanceof Number)) {
            errors.add(key + " must be a number");
        }
    }

    private void requireOneOf(Map<String, Object> f, String key, List<String> errors, String... allowed) {
        String v = str(f, key);
        if (v == null) {
            errors.add(key + " is required");
            return;
        }
        for (String a : allowed) {
            if (a.equals(v)) {
                return;
            }
        }
        errors.add("invalid " + key + ": " + v);
    }

    // --- Book / user helpers -------------------------------------------------

    private Book requireBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("Book not found: " + bookId));
    }

    private Long userId(String username) {
        return userRepository.findByUsername(username).map(User::getId).orElse(null);
    }

    /** Cell-level parse failure — row is invalid, no fields are returned. */
    private static class RowError extends Exception {
        RowError(String message) {
            super(message);
        }
    }
}