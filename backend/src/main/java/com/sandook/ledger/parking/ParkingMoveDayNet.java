package com.sandook.ledger.parking;

import java.time.LocalDate;

/** Row projection for {@link ParkingCashMoveRepository#netByDay}. */
public interface ParkingMoveDayNet {

    LocalDate getDate();

    Long getNetMinor();
}
