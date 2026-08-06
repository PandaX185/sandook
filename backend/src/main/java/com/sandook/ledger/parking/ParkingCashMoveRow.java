package com.sandook.ledger.parking;

import java.time.LocalDate;

/** Row projection for {@link ParkingCashMoveRepository#search}. */
public interface ParkingCashMoveRow {

    Long getId();

    Long getBookId();

    LocalDate getDate();

    String getType();

    Long getAmountMinor();

    String getDescription();

    Long getEnteredBy();

    Long getBalanceMinor();
}
