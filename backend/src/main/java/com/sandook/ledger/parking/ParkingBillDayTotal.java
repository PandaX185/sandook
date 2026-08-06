package com.sandook.ledger.parking;

import java.time.LocalDate;

/** Row projection for {@link ParkingBillRepository#cashTotalsByDay}. */
public interface ParkingBillDayTotal {

    LocalDate getDate();

    Long getCashMinor();
}
