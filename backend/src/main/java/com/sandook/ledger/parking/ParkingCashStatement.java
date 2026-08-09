package com.sandook.ledger.parking;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily parking statement — mirrors the Excel sheet convention, enriched with
 * card bills, booking payments, expense notes, and a range summary.
 * closing = opening + cash bills + card bills − (transfers to shop + salaries + expenses).
 * CLOSING moves are informational snapshots and never change the balance.
 */
public record ParkingCashStatement(
        Long bookId,
        Summary summary,
        List<DayRow> days
) {

    /** Range-wide figures for the header cards. Nullable when not covered by the range. */
    public record Summary(
            long totalBalanceMinor,
            Long todayCashMinor,
            Long todayCardMinor,
            Long monthBillsMinor
    ) {
    }

    public record DayRow(
            LocalDate date,
            long openingMinor,
            long cashBillsMinor,
            long cardBillsMinor,
            long totalBillsMinor,
            long bookingsMinor,
            long transfersToShopMinor,
            long salariesMinor,
            long expensesMinor,
            List<String> expenseNotes,
            long netOutMinor,
            long closingMinor,
            long cumulativeMinor,
            List<String> warnings
    ) {
    }
}
