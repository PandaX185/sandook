package com.sandook.ledger.excel;

/** Result of an import commit: how many rows were inserted vs skipped. */
public record ImportCommitResponse(int inserted, int skipped) {
}
