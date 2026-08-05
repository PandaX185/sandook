package com.sandook.ledger.cash;

import java.time.LocalDate;
import java.util.List;

/** Daily sheet row with computed figures (all minor units) and non-blocking warnings. */
public record CashDayResponse(
        Long id,
        Long bookId,
        LocalDate date,
        long salesMinor,
        long extraMinor,
        long withdrawMinor,
        long depositMinor,
        long netCashMinor,
        long balanceMinor,
        String depositRemarks,
        String ref,
        String notes,
        List<String> warnings
) {

    public static CashDayResponse from(CashDayBalanceRow row, List<String> warnings) {
        long netCashMinor = row.getSalesMinor() + row.getExtraMinor() - row.getWithdrawMinor();
        return new CashDayResponse(
                row.getId(),
                row.getBookId(),
                row.getDate(),
                row.getSalesMinor(),
                row.getExtraMinor(),
                row.getWithdrawMinor(),
                row.getDepositMinor(),
                netCashMinor,
                row.getBalanceMinor(),
                row.getDepositRemarks(),
                row.getRef(),
                row.getNotes(),
                warnings
        );
    }

    public static CashDayResponse from(CashDay day, long balanceMinor, List<String> warnings) {
        long netCashMinor = day.getSalesMinor() + day.getExtraMinor() - day.getWithdrawMinor();
        return new CashDayResponse(
                day.getId(),
                day.getBookId(),
                day.getDate(),
                day.getSalesMinor(),
                day.getExtraMinor(),
                day.getWithdrawMinor(),
                day.getDepositMinor(),
                netCashMinor,
                balanceMinor,
                day.getDepositRemarks(),
                day.getRef(),
                day.getNotes(),
                warnings
        );
    }
}
