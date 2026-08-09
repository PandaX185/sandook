package com.sandook.ledger.parking;

import java.time.LocalDate;

/** Row projection for {@link ParkingBillRepository#search}. */
public interface ParkingBillRow {

    Long getId();

    Long getBookId();

    Long getBookingId();

    String getPlateNo();

    Long getAmountMinor();

    String getPaymentMethod();

    LocalDate getBilledAt();

    Long getEnteredBy();
}
