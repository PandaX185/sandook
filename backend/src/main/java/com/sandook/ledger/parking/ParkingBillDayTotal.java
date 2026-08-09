package com.sandook.ledger.parking;

import java.time.LocalDate;

/** Row projection for {@link ParkingBillRepository#totalsByDay}. */
public interface ParkingBillDayTotal {

    LocalDate getDate();

    Long getCashMinor();

    Long getCardMinor();

    /** Bills linked to a booking (payments for booking intervals). */
    Long getBookingsMinor();
}
