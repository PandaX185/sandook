package com.sandook.ledger.cash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CashDayRepository extends JpaRepository<CashDay, Long> {

        Optional<CashDay> findByBookIdAndId(Long bookId, Long id);

        Optional<CashDay> findByBookIdAndDate(Long bookId, LocalDate date);

        boolean existsByBookIdAndDate(Long bookId, LocalDate date);

        /**
         * Every day row with its running balance (computed in Postgres via window
         * function — never summed in Java). balance = opening + sales + extra −
         * withdraw − deposit, where opening is the cumulative net of all previous
         * days in the same book.
         */
        @Query(value = """
                        SELECT cd.id            AS id,
                               cd.book_id       AS bookId,
                               cd.date          AS date,
                               cd.sales_minor   AS salesMinor,
                               cd.extra_minor   AS extraMinor,
                               cd.withdraw_minor AS withdrawMinor,
                               cd.deposit_minor AS depositMinor,
                               cd.deposit_remarks AS depositRemarks,
                               cd."ref"         AS ref,
                               cd.notes         AS notes,
                               cd.entered_by    AS enteredBy,
                               cd.sales_minor + cd.extra_minor - cd.withdraw_minor - cd.deposit_minor AS balanceMinor
                        FROM cash_days cd
                        WHERE cd.book_id = :bookId
                        ORDER BY cd.date desc
                        """, nativeQuery = true)
        List<CashDayBalanceRow> findWithBalanceByBookId(@Param("bookId") Long bookId);

        /**
         * Opening balance for a day = cumulative net of all strictly-earlier days in
         * the book.
         */
        @Query(value = """
                        SELECT COALESCE(SUM(sales_minor + extra_minor - withdraw_minor - deposit_minor), 0)
                        FROM cash_days
                        WHERE book_id = :bookId AND date < :date
                        """, nativeQuery = true)
        long sumNetBefore(@Param("bookId") Long bookId, @Param("date") LocalDate date);
}
