package com.sandook.ledger.pettycash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PettyCashTransactionRepository extends JpaRepository<PettyCashTransaction, Long> {

        List<PettyCashTransaction> findAllByBookIdOrderByDateDescIdDesc(Long bookId);

        /**
         * Running balance after each transaction (PUT − TAKE), computed in Postgres
         * via a window function — never summed in Java. Ordered by (date, id), the
         * same order as {@link #findAllByBookIdOrderByDateDescIdDesc}.
         */
        @Query(value = """
                        SELECT t.id AS id,
                               SUM(CASE WHEN t.type = 'PUT' THEN t.amount_minor ELSE -t.amount_minor END)
                                   OVER (PARTITION BY t.book_id ORDER BY t.date, t.id) AS balanceMinor
                        FROM petty_cash_transactions t
                        WHERE t.book_id = :bookId
                        ORDER BY t.date, t.id
                        """, nativeQuery = true)
        List<PettyCashBalancePoint> findRunningBalances(@Param("bookId") Long bookId);

        /**
         * Current petty cash balance = SUM(put) − SUM(take). Always computed, never
         * stored.
         */
        @Query(value = """
                        SELECT COALESCE(SUM(CASE WHEN type = 'PUT' THEN amount_minor ELSE -amount_minor END), 0)
                        FROM petty_cash_transactions
                        WHERE book_id = :bookId
                        """, nativeQuery = true)
        long totalBalance(@Param("bookId") Long bookId);

        /** Balance as of a date (inclusive): all transactions on or before it. */
        @Query(value = """
                        SELECT COALESCE(SUM(CASE WHEN type = 'PUT' THEN amount_minor ELSE -amount_minor END), 0)
                        FROM petty_cash_transactions
                        WHERE book_id = :bookId AND date <= :date
                        """, nativeQuery = true)
        long balanceAsOf(@Param("bookId") Long bookId, @Param("date") java.time.LocalDate date);
}
