package com.sandook.ledger.excel;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Excel imports — preview first (no writes), then commit the valid rows.
 * Editor-only, matching every other write endpoint.
 */
@RestController
@RequestMapping("/api/v1/books/{bookId}/imports")
public class ExcelImportController {

    private final ExcelImportService importService;

    public ExcelImportController(ExcelImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/preview")
    @PreAuthorize("hasRole('EDITOR')")
    public ImportPreviewResponse preview(@PathVariable Long bookId,
                                         @RequestParam("file") MultipartFile file) {
        return importService.preview(bookId, file);
    }

    @PostMapping("/commit")
    @PreAuthorize("hasRole('EDITOR')")
    public ImportCommitResponse commit(@PathVariable Long bookId,
                                       @RequestBody ImportCommitRequest request,
                                       Authentication authentication) {
        return importService.commit(bookId, request, authentication.getName());
    }
}
