package com.sandook.ledger.excel;

import java.util.List;

/** Commit payload: the layout + the valid rows from the preview. */
public record ImportCommitRequest(
        ImportLayout layout,
        List<ImportPreviewRow> rows
) {
}
