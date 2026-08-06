package com.sandook.ledger.transfer;

import java.time.Instant;
import java.time.LocalDate;

/** Transfer with its automation linkage (parking move + shop extra), if any. */
public record TransferResponse(
        Long id,
        Long fromBookId,
        Long toBookId,
        LocalDate date,
        long amountMinor,
        String currencyCode,
        String ref,
        boolean linkedParkingMove,
        Long linkedMoveId,
        Long linkedCashDayId,
        Long linkedCashDayExtraMinor,
        Long enteredBy,
        Instant createdAt
) {

    public static TransferResponse from(Transfer t, boolean linkedParkingMove,
                                        Long linkedMoveId, Long linkedCashDayId, Long linkedCashDayExtraMinor) {
        return new TransferResponse(
                t.getId(),
                t.getFromBookId(),
                t.getToBookId(),
                t.getDate(),
                t.getAmountMinor(),
                t.getCurrencyCode(),
                t.getRef(),
                linkedParkingMove,
                linkedMoveId,
                linkedCashDayId,
                linkedCashDayExtraMinor,
                t.getEnteredBy(),
                t.getCreatedAt()
        );
    }
}
