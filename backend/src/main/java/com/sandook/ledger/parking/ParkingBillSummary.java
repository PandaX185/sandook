package com.sandook.ledger.parking;

import java.time.LocalDate;
import java.util.List;

/** Cash vs card totals for a bill range. Money in minor units (fils). */
public record ParkingBillSummary(
        Long bookId,
        LocalDate from,
        LocalDate to,
        long cashMinor,
        long cardMinor,
        long totalMinor,
        long count
) {

    public static ParkingBillSummary from(Long bookId, LocalDate from, LocalDate to,
                                          ParkingBillSummaryRow row) {
        return new ParkingBillSummary(
                bookId,
                from,
                to,
                row.getCashMinor(),
                row.getCardMinor(),
                row.getTotalMinor(),
                row.getCount()
        );
    }
}
