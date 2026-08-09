package com.sandook.ledger.parking;

import java.time.Instant;
import java.time.LocalDate;

/** Booking with its computed payment status (never stored — derived in the service). */
public record ParkingBookingResponse(
        Long id,
        Long bookId,
        String plateNo,
        long monthlyRateMinor,
        ParkingBookingInterval intervalType,
        Integer intervalMonths,
        LocalDate nextDueDate,
        LocalDate paidThroughDate,
        ParkingBookingStatus status,
        boolean active,
        Long enteredBy,
        Instant createdAt
) {

    public static ParkingBookingResponse from(ParkingBooking booking, ParkingBookingStatus status) {
        return new ParkingBookingResponse(
                booking.getId(),
                booking.getBookId(),
                booking.getPlateNo(),
                booking.getMonthlyRateMinor(),
                booking.getIntervalType(),
                booking.getIntervalMonths(),
                booking.getNextDueDate(),
                booking.getPaidThroughDate(),
                status,
                booking.isActive(),
                booking.getEnteredBy(),
                booking.getCreatedAt()
        );
    }
}
