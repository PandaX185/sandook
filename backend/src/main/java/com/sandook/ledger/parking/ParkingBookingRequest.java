package com.sandook.ledger.parking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Create/update payload for a monthly booking. Money in minor units (fils). */
public record ParkingBookingRequest(
        @NotBlank @Size(max = 20) String plateNo,
        @NotNull @PositiveOrZero Long monthlyRateMinor,
        @NotNull LocalDate renewalMonth,
        Boolean active
) {
}
