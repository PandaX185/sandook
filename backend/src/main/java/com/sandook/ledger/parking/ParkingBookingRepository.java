package com.sandook.ledger.parking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParkingBookingRepository extends JpaRepository<ParkingBooking, Long> {

    Optional<ParkingBooking> findByBookIdAndId(Long bookId, Long id);

    List<ParkingBooking> findAllByBookIdOrderByNextDueDateAscIdAsc(Long bookId);

    List<ParkingBooking> findAllByBookIdAndActiveTrueOrderByNextDueDateAscIdAsc(Long bookId);
}
