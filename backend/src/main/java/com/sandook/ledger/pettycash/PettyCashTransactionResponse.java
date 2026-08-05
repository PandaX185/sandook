package com.sandook.ledger.pettycash;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Ledger row with its running balance (minor units). For PUT transactions the
 * linked cash-day withdraw is reported too — the automation that kills manual
 * double-entry between petty cash and the cash in hand sheet.
 */
public record PettyCashTransactionResponse(
        Long id,
        Long bookId,
        LocalDate date,
        String description,
        PettyCashType type,
        long amountMinor,
        String currencyCode,
        long balanceMinor,
        Long linkedCashDayId,
        Long linkedCashDayWithdrawMinor,
        Long enteredBy,
        Instant createdAt
) {

    public static PettyCashTransactionResponse from(PettyCashTransaction tx, long balanceMinor,
                                                    Long linkedCashDayId, Long linkedCashDayWithdrawMinor) {
        return new PettyCashTransactionResponse(
                tx.getId(),
                tx.getBookId(),
                tx.getDate(),
                tx.getDescription(),
                tx.getType(),
                tx.getAmountMinor(),
                tx.getCurrencyCode(),
                balanceMinor,
                linkedCashDayId,
                linkedCashDayWithdrawMinor,
                tx.getEnteredBy(),
                tx.getCreatedAt()
        );
    }
}
