package com.sandook.ledger.common;

import com.sandook.ledger.book.Book;
import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.book.Currency;
import com.sandook.ledger.book.CurrencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the currencies and books tables if they are empty.
 * Runs after {@link BootstrapAdminRunner} (Order(1) vs default 0).
 * Needed in embedded desktop mode where Flyway is disabled and
 * {@code ddl-auto=update} only creates the schema — not seed data.
 */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CurrencyRepository currencyRepository;
    private final BookRepository bookRepository;

    public DataSeeder(CurrencyRepository currencyRepository, BookRepository bookRepository) {
        this.currencyRepository = currencyRepository;
        this.bookRepository = bookRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (currencyRepository.count() > 0) {
            return;
        }

        Currency aed = new Currency();
        aed.setCode("AED");
        aed.setName("UAE Dirham");
        aed.setSymbol("د.إ");
        aed.setDecimalPlaces(2);
        currencyRepository.save(aed);
        log.info("Seeded currency: AED");

        Book shop = new Book();
        shop.setName("Shop");
        shop.setCurrencyCode("AED");
        bookRepository.save(shop);

        Book parking = new Book();
        parking.setName("Parking");
        parking.setCurrencyCode("AED");
        bookRepository.save(parking);

        log.info("Seeded books: Shop, Parking");
    }
}
