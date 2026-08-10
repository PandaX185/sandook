package com.sandook.ledger.common;

/** 400 — malformed input, unrecognized Excel layout, etc. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
