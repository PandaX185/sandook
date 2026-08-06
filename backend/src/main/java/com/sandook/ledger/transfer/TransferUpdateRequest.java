package com.sandook.ledger.transfer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Update payload for a transfer. Books are immutable after creation — a
 * transfer happened between two books; you can fix the amount, date or
 * reference, but not re-point it.
 */
public record TransferUpdateRequest(
        @NotNull LocalDate date,
        @NotNull @Positive Long amountMinor,
        @Size(max = 100) String ref
) {
}
