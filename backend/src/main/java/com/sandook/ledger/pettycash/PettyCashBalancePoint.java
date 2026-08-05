package com.sandook.ledger.pettycash;

/** Row projection for {@link PettyCashTransactionRepository#findRunningBalances}. */
public interface PettyCashBalancePoint {

    Long getId();

    Long getBalanceMinor();
}
