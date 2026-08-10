package com.sandook.ledger.excel;

import java.util.List;
import java.util.Map;

/**
 * One parsed row from an imported sheet. Fields hold typed values
 * (Long minor, String, LocalDate). The layout is per-row — a workbook
 * may contain several layouts across its sheets.
 */
public record ImportPreviewRow(
        int rowNo,
        String sheet,
        ImportLayout layout,
        Map<String, Object> fields,
        boolean valid,
        List<String> errors
) {
}
