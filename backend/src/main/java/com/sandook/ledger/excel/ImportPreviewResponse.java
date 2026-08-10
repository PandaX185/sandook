package com.sandook.ledger.excel;

import java.util.List;

/**
 * Preview of a parsed workbook — no writes happen here.
 * Sheets that did not match any known layout are listed in skippedSheets.
 */
public record ImportPreviewResponse(
        String fileName,
        List<ImportPreviewRow> rows,
        List<String> skippedSheets
) {
}
