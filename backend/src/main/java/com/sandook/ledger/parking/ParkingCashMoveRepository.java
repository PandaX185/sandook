package com.sandook.ledger.parking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ParkingCashMoveRepository extends JpaRepository<ParkingCashMove, Long> {

    Optional<ParkingCashMove> findByBookIdAndId(Long bookId, Long id);

    @Query(value = """
            SELECT m.id            AS id,
                   m.book_id       AS bookId,
                   m.date          AS date,
                   m.type          AS type,
                   m.amount_minor  AS amountMinor,
                   m.description   AS description,
                   m.entered_by    AS enteredBy,
                   COALESCE(SUM(CASE WHEN m.type = 'OPENING' THEN m.amount_minor ELSE -m.amount_minor END)
                       OVER (PARTITION BY m.book_id ORDER BY m.date, m.id), 0) AS balanceMinor
            FROM parking_cash_moves m
            WHERE m.book_id = :bookId
              AND (CAST(:from AS date) IS NULL OR m.date >= :from)
              AND (CAST(:to AS date) IS NULL OR m.date <= :to)
            ORDER BY m.date, m.id
            """, nativeQuery = true)
    List<ParkingCashMoveRow> search(@Param("bookId") Long bookId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    List<ParkingCashMove> findAllByBookIdOrderByDateAscIdAsc(Long bookId);

    List<ParkingCashMove> findAllByBookIdAndDateOrderByIdAsc(Long bookId, LocalDate date);

    List<ParkingCashMove> findAllByBookIdAndTypeOrderByDateAscIdAsc(Long bookId, ParkingCashMoveType type);

    java.util.Optional<ParkingCashMove> findByTransferId(Long transferId);

    /**
     * Net effect of outbound moves (everything except OPENING and CLOSING) for
     * days in a range, per day — feeds the daily cash statement.
     */
    @Query(value = """
            SELECT date,
                   COALESCE(SUM(CASE WHEN type = 'OPENING' THEN amount_minor ELSE -amount_minor END), 0) AS netMinor
            FROM parking_cash_moves
            WHERE book_id = :bookId
              AND type <> 'CLOSING'
              AND (CAST(:from AS date) IS NULL OR date >= :from)
              AND (CAST(:to AS date) IS NULL OR date <= :to)
            GROUP BY date
            ORDER BY date
            """, nativeQuery = true)
    List<ParkingMoveDayNet> netByDay(@Param("bookId") Long bookId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /** Balance carried into a date = net of all moves strictly before it. */
    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN type = 'OPENING' THEN amount_minor ELSE -amount_minor END), 0)
            FROM parking_cash_moves
            WHERE book_id = :bookId AND type <> 'CLOSING' AND date < :date
            """, nativeQuery = true)
    long balanceBefore(@Param("bookId") Long bookId, @Param("date") LocalDate date);
}
