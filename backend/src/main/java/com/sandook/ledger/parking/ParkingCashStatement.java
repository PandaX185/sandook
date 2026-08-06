package com.sandook.ledger.parking;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily parking cash statement — mirrors the Excel sheet convention:
 * closing = opening + cash bills − (transfers to shop + salaries + expenses).
 * CLOSING moves are informational snapshots and never change the balance.
 */
public record ParkingCashStatement(
        Long bookId,
        List<DayRow> days
) {

    public record DayRow(
            LocalDate date,
            long openingMinor,
            long cashBillsMinor,
            long transfersToShopMinor,
            long salariesMinor,
            long expensesMinor,
            long netOutMinor,
            long closingMinor,
            List<String> warnings
    ) {
    }
}
