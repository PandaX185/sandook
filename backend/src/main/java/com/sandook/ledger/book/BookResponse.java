package com.sandook.ledger.book;

public record BookResponse(Long id, String name, String currencyCode) {

    public static BookResponse from(Book book) {
        return new BookResponse(book.getId(), book.getName(), book.getCurrencyCode());
    }
}
