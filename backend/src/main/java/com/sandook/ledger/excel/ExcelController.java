package com.sandook.ledger.excel;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Consolidated Excel export — one workbook with 5 clean sheets.
 * Readable by both roles, matching every other GET endpoint.
 */
@RestController
@RequestMapping("/api/v1/books/{bookId}/exports")
public class ExcelController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExcelExportService exportService;

    public ExcelController(ExcelExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/all")
    public ResponseEntity<byte[]> all(@PathVariable Long bookId) {
        return download(exportService.exportAll(bookId), "sandook_ledger.xlsx");
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
