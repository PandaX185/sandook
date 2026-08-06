package com.sandook.ledger.parking;

/** Row projection for {@link ParkingBillRepository#summarize}. */
public interface ParkingBillSummaryRow {

    Long getCashMinor();

    Long getCardMinor();

    Long getTotalMinor();

    Long getCount();
}
