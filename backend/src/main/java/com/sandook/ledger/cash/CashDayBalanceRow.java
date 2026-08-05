package com.sandook.ledger.cash;

import java.time.LocalDate;

/** Row projection for {@link CashDayRepository#findWithBalanceByBookId}. */
public interface CashDayBalanceRow {

    Long getId();

    Long getBookId();

    LocalDate getDate();

    Long getSalesMinor();

    Long getExtraMinor();

    Long getWithdrawMinor();

    Long getDepositMinor();

    String getDepositRemarks();

    String getRef();

    String getNotes();

    Long getEnteredBy();

    Long getBalanceMinor();
}
