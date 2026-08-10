package com.sandook.ledger.excel;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Excel exports — readable by both roles, matches the original sheet layouts. */
@RestController
@RequestMapping("/api/v1/books/{bookId}/exports")
public class ExcelController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExcelExportService exportService;

    public ExcelController(ExcelExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/daybook")
    public ResponseEntity<byte[]> dayBook(@PathVariable Long bookId,
                                          @RequestParam(required = false) LocalDate from,
                                          @RequestParam(required = false) LocalDate to) {
        return download(exportService.dayBook(bookId, from, to), "parking_daybook.xlsx");
    }

    @GetMapping("/statement")
    public ResponseEntity<byte[]> statement(@PathVariable Long bookId,
                                            @RequestParam(required = false) LocalDate from,
                                            @RequestParam(required = false) LocalDate to) {
        return download(exportService.statement(bookId, from, to), "parking_statement.xlsx");
    }

    @GetMapping("/bookings")
    public ResponseEntity<byte[]> bookings(@PathVariable Long bookId) {
        return download(exportService.bookings(bookId), "parking_bookings.xlsx");
    }

    @GetMapping("/cash-deposit")
    public ResponseEntity<byte[]> cashDeposit(@PathVariable Long bookId,
                                              @RequestParam(required = false) Integer year) {
        return download(exportService.cashDeposit(bookId, year), "cash_deposit.xlsx");
    }

    @GetMapping("/petty-cash")
    public ResponseEntity<byte[]> pettyCash(@PathVariable Long bookId,
                                            @RequestParam(required = false) Integer year) {
        return download(exportService.pettyCash(bookId, year), "petty_cash.xlsx");
    }

    private ResponseEntity<byte[]> download(byte[] body, String filename) {
        String disposition = "attachment; filename=\"" + filename + "\""
                + "; filename*=UTF-8''" + encode(filename);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(body);
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
