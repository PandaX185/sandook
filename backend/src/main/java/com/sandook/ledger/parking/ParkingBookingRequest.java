package com.sandook.ledger.parking;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Create/update payload for a booking. Money in minor units (fils). */
public record ParkingBookingRequest(
        @NotBlank @Size(max = 20) String plateNo,
        @NotNull @PositiveOrZero Long monthlyRateMinor,
        @NotNull ParkingBookingInterval intervalType,
        @Min(1) @Max(24) Integer intervalMonths,
        @NotNull LocalDate nextDueDate,
        Boolean active
) {

    /** CUSTOM intervals must carry a concrete month count (1–24). */
    @AssertTrue(message = "intervalMonths (1-24) is required for CUSTOM interval")
    public boolean isCustomIntervalValid() {
        return intervalType != ParkingBookingInterval.CUSTOM
                || (intervalMonths != null && intervalMonths >= 1 && intervalMonths <= 24);
    }
}
