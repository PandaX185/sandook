package com.sandook.ledger.parking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Create/update payload for a parking cash move. Money in minor units (fils).
 * SALARY moves must carry {@code salaryPayments} whose sum equals
 * {@code amountMinor} (validated in the service). TRANSFER_TO_SHOP moves cannot
 * be written via the API at all — only the transfer automation creates them.
 */
public record ParkingCashMoveRequest(
        @NotNull LocalDate date,
        @NotNull ParkingCashMoveType type,
        @NotNull @Positive Long amountMinor,
        @Size(max = 255) String description,
        @Valid List<SalaryPaymentRequest> salaryPayments
) {

    /** One salary split row (e.g. Iqpal / Habib / Raseem). */
    public record SalaryPaymentRequest(
            @NotNull @Size(max = 100) String person,
            @NotNull @Positive Long amountMinor
    ) {
    }
}
