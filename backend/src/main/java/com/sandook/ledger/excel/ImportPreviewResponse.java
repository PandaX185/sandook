package com.sandook.ledger.excel;

import java.util.List;

/** Preview of a parsed workbook — no writes happen here. */
public record ImportPreviewResponse(
        ImportLayout layout,
        String fileName,
        List<ImportPreviewRow> rows
) {
}
