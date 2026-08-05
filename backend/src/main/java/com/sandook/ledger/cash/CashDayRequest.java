package com.sandook.ledger.cash;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Create/update payload for a daily cash sheet row. All money in minor units (fils). */
public record CashDayRequest(
        @NotNull LocalDate date,
        @NotNull @PositiveOrZero Long salesMinor,
        @NotNull @PositiveOrZero Long extraMinor,
        @NotNull @PositiveOrZero Long withdrawMinor,
        @NotNull @PositiveOrZero Long depositMinor,
        @Size(max = 255) String depositRemarks,
        @Size(max = 100) String ref,
        String notes
) {
}
