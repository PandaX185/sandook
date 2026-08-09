package com.sandook.ledger.parking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Pay a parking booking: creates ONE bill for the full amount (rate × months,
 * editable for discounts), then advances the booking's due dates.
 * Money in minor units (fils).
 */
public record ParkingBookingPayRequest(
        @NotNull @Positive Long amountMinor,
        @NotNull PaymentMethod paymentMethod,
        LocalDate paidAt
) {
}
