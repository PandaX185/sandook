package com.sandook.ledger.excel;

import java.util.List;

/** Commit payload: the valid rows from the preview, each carrying its own layout. */
public record ImportCommitRequest(
        List<ImportPreviewRow> rows
) {
}
