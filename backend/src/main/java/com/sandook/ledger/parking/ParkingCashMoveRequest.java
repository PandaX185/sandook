package com.sandook.ledger.parking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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

    /** Cash out (EXPENSE/SALARY) must say what it was for. */
    @AssertTrue(message = "description is required for EXPENSE and SALARY moves")
    public boolean isDescriptionRequiredForCashOut() {
        boolean cashOut = type == ParkingCashMoveType.EXPENSE || type == ParkingCashMoveType.SALARY;
        return !cashOut || (description != null && !description.isBlank());
    }

    /** One salary split row (e.g. Alice / Bob / Charlie). */
    public record SalaryPaymentRequest(
            @NotNull @Size(max = 100) String person,
            @NotNull @Positive Long amountMinor
    ) {
    }
}
