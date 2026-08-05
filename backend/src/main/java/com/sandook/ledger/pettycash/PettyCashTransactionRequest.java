package com.sandook.ledger.pettycash;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Create/update payload for a petty cash ledger entry. Money in minor units (fils). */
public record PettyCashTransactionRequest(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 255) String description,
        @NotNull PettyCashType type,
        @NotNull @Positive Long amountMinor
) {
}
