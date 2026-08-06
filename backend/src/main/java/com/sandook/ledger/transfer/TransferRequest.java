package com.sandook.ledger.transfer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Create payload for a transfer between two books.
 * <p>
 * {@code linkParkingMove} triggers automation #2: one click records the money
 * moving from the parking book to the shop book in all three ledgers —
 * {@code transfers} + a {@code TRANSFER_TO_SHOP} parking cash move on the
 * from-book + {@code extra_minor} on the to-book's cash day for that date.
 */
public record TransferRequest(
        @NotNull Long fromBookId,
        @NotNull Long toBookId,
        @NotNull LocalDate date,
        @NotNull @Positive Long amountMinor,
        @Size(max = 100) String ref,
        Boolean linkParkingMove
) {
}
