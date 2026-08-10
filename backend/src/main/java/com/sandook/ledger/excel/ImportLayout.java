package com.sandook.ledger.excel;

/** Detected layout of an imported workbook, based on header row + sheet names. */
public enum ImportLayout {
    DAY_BOOK,
    BOOKING_SHEET,
    CASH_STATEMENT,
    CASH_DEPOSIT,
    PETTY_CASH
}
