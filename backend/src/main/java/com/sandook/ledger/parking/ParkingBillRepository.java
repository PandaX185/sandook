package com.sandook.ledger.parking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ParkingBillRepository extends JpaRepository<ParkingBill, Long> {

    Optional<ParkingBill> findByBookIdAndId(Long bookId, Long id);

    List<ParkingBill> findByBookingIdOrderByBilledAtAscIdAsc(Long bookingId);

    @Query(value = """
            SELECT b.id             AS id,
                   b.book_id        AS bookId,
                   b.booking_id     AS bookingId,
                   b.plate_no       AS plateNo,
                   b.amount_minor   AS amountMinor,
                   b.payment_method AS paymentMethod,
                   b.billed_at      AS billedAt,
                   b.entered_by     AS enteredBy
            FROM parking_bills b
            WHERE b.book_id = :bookId
              AND (CAST(:from AS date) IS NULL OR b.billed_at >= :from)
              AND (CAST(:to AS date) IS NULL OR b.billed_at <= :to)
              AND (CAST(:plate AS text) IS NULL OR b.plate_no ILIKE '%' || :plate || '%')
            ORDER BY b.billed_at, b.id
            """, nativeQuery = true)
    List<ParkingBillRow> search(@Param("bookId") Long bookId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to,
                                @Param("plate") String plate);

    /** Cash vs card split for a range — summed in Postgres, never in Java. */
    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN payment_method = 'CASH' THEN amount_minor END), 0) AS cashMinor,
                   COALESCE(SUM(CASE WHEN payment_method = 'CARD' THEN amount_minor END), 0) AS cardMinor,
                   COALESCE(SUM(amount_minor), 0)                                           AS totalMinor,
                   COUNT(*)                                                                 AS count
            FROM parking_bills
            WHERE book_id = :bookId
              AND (CAST(:from AS date) IS NULL OR billed_at >= :from)
              AND (CAST(:to AS date) IS NULL OR billed_at <= :to)
            """, nativeQuery = true)
    ParkingBillSummaryRow summarize(@Param("bookId") Long bookId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    /** Cash bills per day in a range — feeds the daily cash statement. */
    @Query(value = """
            SELECT billed_at AS date, COALESCE(SUM(amount_minor), 0) AS cashMinor
            FROM parking_bills
            WHERE book_id = :bookId AND payment_method = 'CASH'
              AND (CAST(:from AS date) IS NULL OR billed_at >= :from)
              AND (CAST(:to AS date) IS NULL OR billed_at <= :to)
            GROUP BY billed_at
            ORDER BY billed_at
            """, nativeQuery = true)
    List<ParkingBillDayTotal> cashTotalsByDay(@Param("bookId") Long bookId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}
