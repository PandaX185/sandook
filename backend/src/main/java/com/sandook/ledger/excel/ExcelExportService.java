package com.sandook.ledger.excel;

import com.sandook.ledger.cash.CashDayResponse;
import com.sandook.ledger.cash.CashDayService;
import com.sandook.ledger.parking.ParkingBillResponse;
import com.sandook.ledger.parking.ParkingBillService;
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
import java.util.List;

/**
 * Builds the single consolidated Excel workbook: 5 clean sheets
 * (Day Book | Cash Statement | Bookings | Cash Deposit | Petty Cash),
 * one header row, no title/total rows, D/M/YYYY dates, money in AED with 2 decimals.
 */
@Service
public class ExcelExportService {

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

    /** The full consolidated workbook for a book — all data, no filters. */
    public byte[] exportAll(Long bookId) {
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle dateStyle = dateStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);

            dayBookSheet(wb, bookId, dateStyle, moneyStyle);
            statementSheet(wb, bookId, dateStyle, moneyStyle);
            bookingsSheet(wb, bookId, dateStyle, moneyStyle);
            cashDepositSheet(wb, bookId, dateStyle, moneyStyle);
            pettyCashSheet(wb, bookId, dateStyle, moneyStyle);

            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build consolidated export", e);
        }
    }

    // --- 1. Day Book ---------------------------------------------------------

    private void dayBookSheet(Workbook wb, Long bookId, CellStyle dateStyle, CellStyle moneyStyle) {
        List<ParkingBillResponse> bills = billService.list(bookId, null, null, null, null);
        Sheet sheet = wb.createSheet("Day Book");
        writeHeader(wb, sheet, "DATE", "CAR NUMBER", "DURATION", "TERM", "CASH", "CARD", "PAYMENT STATUS");
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
    }

    // --- 2. Cash statement ---------------------------------------------------

    private void statementSheet(Workbook wb, Long bookId, CellStyle dateStyle, CellStyle moneyStyle) {
        List<ParkingCashMoveResponse> moves = cashMoveService.list(bookId, null, null);
        Sheet sheet = wb.createSheet("Cash Statement");
        writeHeader(wb, sheet, "DATE", "AMOUNT", "REMARKS", "BALANCE");
        int r = 1;
        for (ParkingCashMoveResponse move : moves) {
            Row row = sheet.createRow(r++);
            dateCell(row, 0, move.date(), dateStyle);
            moneyCell(row, 1, signedAmount(move), moneyStyle);
            String remarks = move.description() != null && !move.description().isBlank()
                    ? move.description()
                    : move.type().name().replace('_', ' ');
            row.createCell(2).setCellValue(remarks);
            moneyCell(row, 3, move.balanceMinor(), moneyStyle);
        }
        autoSize(sheet, 4);
    }

    private long signedAmount(ParkingCashMoveResponse move) {
        return switch (move.type()) {
            case OPENING -> move.amountMinor();
            case TRANSFER_TO_SHOP, SALARY, EXPENSE, CLOSING -> -move.amountMinor();
        };
    }

    // --- 3. Bookings ---------------------------------------------------------

    private void bookingsSheet(Workbook wb, Long bookId, CellStyle dateStyle, CellStyle moneyStyle) {
        List<ParkingBookingResponse> bookings = bookingService.list(bookId, null, null);
        Sheet sheet = wb.createSheet("Bookings");
        writeHeader(wb, sheet, "SL.NO", "CAR PLATE", "VALID FROM", "VALID TO", "TOTAL PRICE",
                "MONTHLY AMOUNT", "TERM", "PAYMENT STATUS");
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
            int months = booking.intervalType().months(booking.intervalMonths());
            moneyCell(row, 4, booking.monthlyRateMinor() * months, moneyStyle);
            moneyCell(row, 5, booking.monthlyRateMinor(), moneyStyle);
            row.createCell(6).setCellValue(termLabel(booking));
            row.createCell(7).setCellValue(booking.status().name());
        }
        autoSize(sheet, 8);
    }

    private String termLabel(ParkingBookingResponse booking) {
        return switch (booking.intervalType()) {
            case MONTHLY -> "MONTHLY";
            case THREE_MONTHS -> "3 MONTHS";
            case SIX_MONTHS -> "6 MONTHS";
            case CUSTOM -> "CUSTOM (" + booking.intervalMonths() + " MONTHS)";
        };
    }

    // --- 4. Cash deposit -----------------------------------------------------

    private void cashDepositSheet(Workbook wb, Long bookId, CellStyle dateStyle, CellStyle moneyStyle) {
        List<CashDayResponse> days = cashDayService.list(bookId);
        Sheet sheet = wb.createSheet("Cash Deposit");
        writeHeader(wb, sheet, "DATE", "SALES AMOUNT", "EXTRA", "WITHDRAW", "NET CASH",
                "DEPOSIT AMOUNT", "BALANCE", "REMARKS", "REFERENCE", "NOTES");
        int r = 1;
        for (CashDayResponse day : days) {
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

    // --- 5. Petty cash -------------------------------------------------------

    private void pettyCashSheet(Workbook wb, Long bookId, CellStyle dateStyle, CellStyle moneyStyle) {
        List<PettyCashTransactionResponse> txs = pettyCashService.list(bookId);
        Sheet sheet = wb.createSheet("Petty Cash");
        writeHeader(wb, sheet, "DATE", "AMOUNT", "REMARKS", "BALANCE");
        int r = 1;
        for (PettyCashTransactionResponse tx : txs) {
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

    // --- helpers -------------------------------------------------------------

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
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("d/m/yyyy"));
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
