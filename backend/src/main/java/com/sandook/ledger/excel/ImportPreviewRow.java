package com.sandook.ledger.excel;

import java.util.List;
import java.util.Map;

/** One parsed row from an imported sheet. Fields hold typed values (Long minor, String, LocalDate). */
public record ImportPreviewRow(
        int rowNo,
        String sheet,
        Map<String, Object> fields,
        boolean valid,
        List<String> errors
) {
}
