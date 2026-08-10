package com.sandook.ledger.excel;

import com.sandook.ledger.cash.CashDayResponse;
import com.sandook.ledger.cash.CashDayService;
import com.sandook.ledger.parking.ParkingBillResponse;
import com.sandook.ledger.parking.ParkingBillService;
import com.sandook.ledger.parking.ParkingBookingInterval;
import com.sandook.ledger.parking.ParkingBookingResponse;
import com.sandook.ledger.parking.ParkingBookingService;
import com.sandook.ledger.parking.ParkingCashMoveResponse;
import com.sandook.ledger.parking.ParkingCashMoveService;
import com.sandook.ledger.parking.PaymentMethod;
import com.sandook.ledger.pettycash.PettyCashService;
import com.sandook.ledger.pettycash.PettyCashTransactionResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the 5 Excel exports matching the original sheet layouts exactly.
 * Money is exported in AED with 2 decimals (converted from minor units).
 */
@Service
public class ExcelExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/mm/yyyy");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMM yyyy");

    private final ParkingBillService billService;
    private final ParkingCashMoveService cashMoveService;
    private final ParkingBookingService bookingService;
    private final PettyCashService pettyCashService;
    private final CashDayService cashDayService;

    public ExcelExportService(ParkingBillService billService,
                              ParkingCashMoveService cashMoveService,
                              ParkingBookingService bookingService,
                              PettyCashService pettyCashService,
                              CashDayService cashDayService) {
        this.billService = billService;
        this.cashMoveService = cashMoveService;
        this.bookingService = bookingService;
        this.pettyCashService = pettyCashService;
        this.cashDayService = cashDayService;
    }

    // --- 1. Parking day book -------------------------------------------------

    public byte[] dayBook(Long bookId, LocalDate from, LocalDate to) {
        List<ParkingBillResponse> bills = billService.list(bookId, from, to, null, null);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Day Book");
            writeHeader(wb, sheet, "DATE", "CAR NUMBER", "DURATION", "TERM", "CASH", "CARD", "PAYMENT STATUS");
            CellStyle dateStyle = dateStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);
            int r = 1;
            for (ParkingBillResponse bill : bills) {
                Row row = sheet.createRow(r++);
                dateCell(row, 0, bill.billedAt(), dateStyle);
                row.createCell(1).setCellValue(bill.plateNo());
                row.createCell(2).setCellValue(""); // duration not tracked
                row.createCell(3).setCellValue(""); // term not tracked on bills
                if (bill.paymentMethod() == PaymentMethod.CASH) {
                    moneyCell(row, 4, bill.amountMinor(), moneyStyle);
                } else {
                    moneyCell(row, 5, bill.amountMinor(), moneyStyle);
                }
                row.createCell(6).setCellValue("P");
            }
            autoSize(sheet, 7);
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build day book export", e);
        }
    }

    // --- 2. Cash statement ----------------------------------------------------

    public byte[] statement(Long bookId, LocalDate from, LocalDate to) {
        List<ParkingCashMoveResponse> moves = cashMoveService.list(bookId, from, to);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("cash statement");
            writeHeader(wb, sheet, "Date", "Amount", "Remarks", "Balance");
            CellStyle dateStyle = dateStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);
            int r = 1;
            for (ParkingCashMoveResponse move : moves) {
                Row row = sheet.createRow(r++);
                dateCell(row, 0, move.date(), dateStyle);
                long signed = signedAmount(move);
                moneyCell(row, 1, signed, moneyStyle);
                String remarks = move.description() != null && !move.description().isBlank()
                        ? move.description()
                        : move.type().name().replace('_', ' ');
                row.createCell(2).setCellValue(remarks);
                moneyCell(row, 3, move.balanceMinor(), moneyStyle);
            }
            autoSize(sheet, 4);
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build statement export", e);
        }
    }

    private long signedAmount(ParkingCashMoveResponse move) {
        return switch (move.type()) {
            case OPENING -> move.amountMinor();
            case TRANSFER_TO_SHOP, SALARY, EXPENSE, CLOSING -> -move.amountMinor();
        };
    }

    // --- 3. Booking sheet -----------------------------------------------------

    public byte[] bookings(Long bookId) {
        List<ParkingBookingResponse> bookings = bookingService.list(bookId, null, null);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("BOOKING SHEET");
            writeHeader(wb, sheet, "SL.NO", "Car Plate Number", "Due date FROM", "Due date TO",
                    "Total Price", "monthly amount", "Term (DURATION OF RENT)", "Payment status");
            CellStyle dateStyle = dateStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);
            int r = 1;
            int sl = 1;
            for (ParkingBookingResponse booking : bookings) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(sl++);
                row.createCell(1).setCellValue(booking.plateNo());
                dateCell(row, 2, booking.nextDueDate(), dateStyle);
                if (booking.paidThroughDate() != null) {
                    dateCell(row, 3, booking.paidThroughDate(), dateStyle);
                }
                int months = monthsCovered(booking);
                moneyCell(row, 4, booking.monthlyRateMinor() * months, moneyStyle);
                moneyCell(row, 5, booking.monthlyRateMinor(), moneyStyle);
                row.createCell(6).setCellValue(termLabel(booking));
                row.createCell(7).setCellValue(booking.status().name());
            }
            autoSize(sheet, 8);
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build bookings export", e);
        }
    }

    private int monthsCovered(ParkingBookingResponse booking) {
        return booking.intervalType().months(booking.intervalMonths());
    }

    private String termLabel(ParkingBookingResponse booking) {
        return switch (booking.intervalType()) {
            case MONTHLY -> "MONTHLY";
            case THREE_MONTHS -> "3 MONTHS";
            case SIX_MONTHS -> "6 MONTHS";
            case CUSTOM -> "CUSTOM (" + booking.intervalMonths() + " MONTHS)";
        };
    }

    // --- 4. Cash deposit (one sheet per month) --------------------------------

    public byte[] cashDeposit(Long bookId, Integer year) {
        List<CashDayResponse> days = cashDayService.list(bookId);
        int targetYear = year != null ? year : LocalDate.now().getYear();
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle dateStyle = dateStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);
            Map<YearMonth, List<CashDayResponse>> byMonth = groupByMonth(days, targetYear);
            if (byMonth.isEmpty()) {
                wb.createSheet("No data");
            }
            for (Map.Entry<YearMonth, List<CashDayResponse>> entry : byMonth.entrySet()) {
                Sheet sheet = wb.createSheet(entry.getKey().format(MONTH_FMT));
                writeHeader(wb, sheet, "Date", "Sales Amount", "Extra Amount take fr", "Withdraw",
                        "Net Cash", "Deposit Amount", "Balance", "Deposit Remarks",
                        "Reference/Receipt No", "Notes");
                int r = 1;
                for (CashDayResponse day : entry.getValue()) {
                    Row row = sheet.createRow(r++);
                    dateCell(row, 0, day.date(), dateStyle);
                    moneyCell(row, 1, day.salesMinor(), moneyStyle);
                    moneyCell(row, 2, day.extraMinor(), moneyStyle);
                    moneyCell(row, 3, day.withdrawMinor(), moneyStyle);
                    moneyCell(row, 4, day.netCashMinor(), moneyStyle);
                    moneyCell(row, 5, day.depositMinor(), moneyStyle);
                    moneyCell(row, 6, day.balanceMinor(), moneyStyle);
                    row.createCell(7).setCellValue(nullSafe(day.depositRemarks()));
                    row.createCell(8).setCellValue(nullSafe(day.ref()));
                    row.createCell(9).setCellValue(nullSafe(day.notes()));
                }
                autoSize(sheet, 10);
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build cash deposit export", e);
        }
    }

    // --- 5. Petty cash (one sheet per month) ----------------------------------

    public byte[] pettyCash(Long bookId, Integer year) {
        List<PettyCashTransactionResponse> txs = pettyCashService.list(bookId);
        int targetYear = year != null ? year : LocalDate.now().getYear();
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle dateStyle = dateStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);
            Map<YearMonth, List<PettyCashTransactionResponse>> byMonth = groupByMonth(txs, targetYear);
            if (byMonth.isEmpty()) {
                wb.createSheet("No data");
            }
            for (Map.Entry<YearMonth, List<PettyCashTransactionResponse>> entry : byMonth.entrySet()) {
                Sheet sheet = wb.createSheet(entry.getKey().format(MONTH_FMT));
                writeHeader(wb, sheet, "Date", "Amount", "Remarks", "Balance");
                int r = 1;
                for (PettyCashTransactionResponse tx : entry.getValue()) {
                    Row row = sheet.createRow(r++);
                    dateCell(row, 0, tx.date(), dateStyle);
                    long signed = tx.type() == com.sandook.ledger.pettycash.PettyCashType.PUT
                            ? tx.amountMinor()
                            : -tx.amountMinor();
                    moneyCell(row, 1, signed, moneyStyle);
                    row.createCell(2).setCellValue(nullSafe(tx.description()));
                    moneyCell(row, 3, tx.balanceMinor(), moneyStyle);
                }
                autoSize(sheet, 4);
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build petty cash export", e);
        }
    }

    // --- helpers --------------------------------------------------------------

    private <T> Map<YearMonth, List<T>> groupByMonth(List<T> items, int year) {
        Map<YearMonth, List<T>> byMonth = new TreeMap<>();
        for (T item : items) {
            LocalDate date = dateOf(item);
            if (date == null || date.getYear() != year) {
                continue;
            }
            byMonth.computeIfAbsent(YearMonth.from(date), k -> new java.util.ArrayList<>()).add(item);
        }
        return byMonth;
    }

    private LocalDate dateOf(Object item) {
        if (item instanceof CashDayResponse d) {
            return d.date();
        }
        if (item instanceof PettyCashTransactionResponse t) {
            return t.date();
        }
        return null;
    }

    private void writeHeader(Workbook wb, Sheet sheet, String... labels) {
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        Row row = sheet.createRow(0);
        for (int i = 0; i < labels.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(labels[i]);
            cell.setCellStyle(style);
        }
    }

    private CellStyle dateStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
        return style;
    }

    private CellStyle moneyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private void dateCell(Row row, int idx, LocalDate date, CellStyle style) {
        if (date == null) {
            return;
        }
        Cell cell = row.createCell(idx);
        cell.setCellValue(java.util.Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        cell.setCellStyle(style);
    }

    private void moneyCell(Row row, int idx, long minor, CellStyle style) {
        Cell cell = row.createCell(idx);
        cell.setCellValue(BigDecimal.valueOf(minor, 2).setScale(2, RoundingMode.HALF_UP).doubleValue());
        cell.setCellStyle(style);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private byte[] toBytes(Workbook wb) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }
}
