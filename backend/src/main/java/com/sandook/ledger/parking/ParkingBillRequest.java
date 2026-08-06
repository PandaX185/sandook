package com.sandook.ledger.parking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Create/update payload for a parking bill. Money in minor units (fils). */
public record ParkingBillRequest(
        @NotBlank @Size(max = 20) String plateNo,
        @NotNull @Positive Long amountMinor,
        @NotNull PaymentMethod paymentMethod,
        @NotNull LocalDate billedAt
) {
}
