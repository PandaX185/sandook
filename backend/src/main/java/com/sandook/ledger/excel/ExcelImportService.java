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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preview-then-commit Excel import matching the 5 original sheet layouts.
 * Preview parses + validates every row and writes NOTHING; commit inserts the
 * valid rows transactionally, re-validating each one defensively.
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

    // --- Preview -------------------------------------------------------------

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
            Sheet first = firstSheet(wb);
            ImportLayout layout = detectLayout(first);
            List<ImportPreviewRow> rows = new ArrayList<>();
            Set<LocalDate> seenCashDates = new HashSet<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                if (sheet.getLastRowNum() < 1) {
                    continue; // header-only or empty sheet
                }
                Map<String, Integer> headers = headerMap(sheet);
                List<String> missing = missingColumns(layout, headers);
                if (!missing.isEmpty()) {
                    rows.add(new ImportPreviewRow(0, sheet.getSheetName(), Map.of(), false,
                            List.of("Sheet headers do not match " + layout + " — missing: "
                                    + String.join(", ", missing))));
                    continue;
                }
                parseSheet(sheet, layout, headers, bookId, seenCashDates, rows);
            }
            if (rows.isEmpty()) {
                throw new BadRequestException("No data rows found in the workbook");
            }
            return new ImportPreviewResponse(layout, name, rows);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel preview failed for book {}", bookId, e);
            throw new BadRequestException("Could not read Excel file: " + e.getMessage());
        }
    }

    private Sheet firstSheet(Workbook wb) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet sheet = wb.getSheetAt(i);
            if (sheet.getLastRowNum() >= 0) {
                return sheet;
            }
        }
        throw new BadRequestException("Workbook has no sheets");
    }

    private ImportLayout detectLayout(Sheet sheet) {
        Map<String, Integer> headers = headerMap(sheet);
        if (headers.containsKey("car number") && headers.containsKey("payment status")) {
            return ImportLayout.DAY_BOOK;
        }
        if (headers.containsKey("car plate number") && headers.containsKey("due date from")) {
            return ImportLayout.BOOKING_SHEET;
        }
        if (headers.containsKey("sales amount") && headers.containsKey("deposit amount")) {
            return ImportLayout.CASH_DEPOSIT;
        }
        String sheetName = sheet.getSheetName().toLowerCase(Locale.ROOT);
        if (headers.containsKey("amount") && headers.containsKey("remarks") && headers.containsKey("balance")) {
            return sheetName.contains("statement") ? ImportLayout.CASH_STATEMENT : ImportLayout.PETTY_CASH;
        }
        throw new BadRequestException("Unrecognized Excel layout — expected a Day Book, Booking Sheet, "
                + "cash statement, cash deposit, or petty cash sheet");
    }

    /** Required columns for each layout — a sheet missing any of them is rejected whole. */
    private List<String> missingColumns(ImportLayout layout, Map<String, Integer> headers) {
        List<String> required = switch (layout) {
            case DAY_BOOK -> List.of("date", "car number", "cash", "card", "payment status");
            case BOOKING_SHEET -> List.of("car plate number", "due date from", "monthly amount");
            case CASH_STATEMENT -> List.of("date", "amount");
            case CASH_DEPOSIT -> List.of("date", "sales amount", "deposit amount");
            case PETTY_CASH -> List.of("date", "amount", "remarks");
        };
        return required.stream().filter(c -> !headers.containsKey(c)).toList();
    }

    private Map<String, Integer> headerMap(Sheet sheet) {
        Map<String, Integer> map = new LinkedHashMap<>();
        Row row = sheet.getRow(0);
        if (row == null) {
            return map;
        }
        for (Cell cell : row) {
            String header = normalize(text(cell));
            if (!header.isEmpty() && !map.containsKey(header)) {
                map.put(header, cell.getColumnIndex());
            }
        }
        return map;
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
                rows.add(new ImportPreviewRow(r + 1, sheet.getSheetName(), Map.of(), false,
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
            rows.add(new ImportPreviewRow(r + 1, sheet.getSheetName(), fields, errors.isEmpty(), errors));
        }
    }

    // --- Field extraction (cells -> typed map) -------------------------------

    private Map<String, Object> dayBookFields(Row row, Map<String, Integer> h) throws RowError {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("date", iso(requiredDate(row, h.get("date"), "DATE")));
        f.put("plateNo", requiredText(row, h.get("car number"), "CAR NUMBER", MAX_PLATE));
        Long cash = money(row.getCell(h.get("cash")));
        Long card = money(row.getCell(h.get("card")));
        if (cash != null && card != null) {
            throw new RowError("both CASH and CARD columns are filled — use one");
        }
        if (cash == null && card == null) {
            throw new RowError("no amount in CASH or CARD column");
        }
        f.put("amountMinor", cash != null ? cash : card);
        f.put("paymentMethod", cash != null ? "CASH" : "CARD");
        f.put("paymentStatus", normalize(text(row.getCell(h.get("payment status")))));
        return f;
    }

    private Map<String, Object> bookingFields(Row row, Map<String, Integer> h) throws RowError {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("plateNo", requiredText(row, h.get("car plate number"), "Car Plate Number", MAX_PLATE));
        f.put("nextDueDate", iso(requiredDate(row, h.get("due date from"), "Due date FROM")));
        LocalDate paidThrough = date(row.getCell(h.get("due date to")));
        f.put("paidThroughDate", paidThrough == null ? null : iso(paidThrough));
        Long monthly = requiredMoney(row, h.get("monthly amount"), "monthly amount");
        if (monthly < 0) {
            throw new RowError("monthly amount must not be negative");
        }
        f.put("monthlyRateMinor", monthly);
        String term = normalize(text(row.getCell(h.get("term (duration of rent)"))));
        f.put("intervalType", parseTerm(term, f));
        String status = normalize(text(row.getCell(h.get("payment status"))));
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

    /** Term cell: MONTHLY / 3 MONTHS / 6 MONTHS / CUSTOM (N MONTHS). Empty -> MONTHLY. */
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

    private Map<String, Object> statementFields(Row row, Map<String, Integer> h) throws RowError {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("date", iso(requiredDate(row, h.get("date"), "Date")));
        Long amount = requiredMoney(row, h.get("amount"), "Amount");
        if (amount == 0) {
            throw new RowError("Amount must not be zero");
        }
        String remarks = text(row.getCell(h.get("remarks")));
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

    private Map<String, Object> cashDepositFields(Row row, Map<String, Integer> h) throws RowError {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("date", iso(requiredDate(row, h.get("date"), "Date")));
        f.put("salesMinor", nonNegative(row, h.get("sales amount"), "Sales Amount"));
        f.put("extraMinor", nonNegative(row, h.get("extra amount take fr"), "Extra Amount"));
        f.put("withdrawMinor", nonNegative(row, h.get("withdraw"), "Withdraw"));
        f.put("depositMinor", nonNegative(row, h.get("deposit amount"), "Deposit Amount"));
        f.put("depositRemarks", capped(row, h.get("deposit remarks"), "Deposit Remarks", MAX_TEXT));
        f.put("ref", capped(row, h.get("reference/receipt no"), "Reference/Receipt No", MAX_REF));
        f.put("notes", text(row.getCell(h.get("notes"))));
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

    // --- Validation (typed map -> errors; shared by preview and commit) ------

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

    // --- Commit --------------------------------------------------------------

    @Transactional
    public ImportCommitResponse commit(Long bookId, ImportCommitRequest request, String username) {
        if (request == null || request.layout() == null || request.rows() == null) {
            throw new BadRequestException("Import commit body must include layout and rows");
        }
        Book book = requireBook(bookId);
        Long enteredBy = userId(username);
        int inserted = 0;
        int skipped = 0;
        Set<LocalDate> seenCashDates = new HashSet<>();
        for (ImportPreviewRow row : request.rows()) {
            Map<String, Object> f = new LinkedHashMap<>(row.fields());
            List<String> errors = validate(request.layout(), f, bookId);
            if (!errors.isEmpty()) {
                skipped++;
                continue;
            }
            if (request.layout() == ImportLayout.CASH_DEPOSIT) {
                LocalDate d = date(f, "date");
                if (!seenCashDates.add(d)) {
                    skipped++;
                    continue;
                }
            }
            insert(request.layout(), book, f, enteredBy);
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
            // Plain serial number — treat as an Excel date.
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
            if (parts[0].trim().length() == 4) { // ISO yyyy-MM-dd
                return LocalDate.of(a, b, y);
            }
            return LocalDate.of(y, b, a); // day first, like the originals
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new RowError("invalid date: " + value + " (expected DD/MM/YYYY)");
        }
    }

    private LocalDate requiredDate(Row row, Integer idx, String label) throws RowError {
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
        Long value = money(row.getCell(idx));
        if (value == null) {
            throw new RowError(label + " is required");
        }
        return value;
    }

    private Long nonNegative(Row row, Integer idx, String label) throws RowError {
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
