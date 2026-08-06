package com.sandook.ledger.parking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ParkingBookingRepository extends JpaRepository<ParkingBooking, Long> {

    Optional<ParkingBooking> findByBookIdAndId(Long bookId, Long id);

    List<ParkingBooking> findAllByBookIdOrderByRenewalMonthAscIdAsc(Long bookId);

    List<ParkingBooking> findAllByBookIdAndActiveTrueOrderByRenewalMonthAscIdAsc(Long bookId);

    /** Active bookings due on or before the given month start (renewal month ≤ cutoff). */
    List<ParkingBooking> findAllByBookIdAndActiveTrueAndRenewalMonthLessThanEqualOrderByRenewalMonthAscIdAsc(
            Long bookId, LocalDate cutoff);
}
