package com.sandook.ledger.parking;

import java.time.Instant;
import java.time.LocalDate;

/** Monthly booking with its computed due flag. */
public record ParkingBookingResponse(
        Long id,
        Long bookId,
        String plateNo,
        long monthlyRateMinor,
        LocalDate renewalMonth,
        boolean active,
        boolean due,
        Long enteredBy,
        Instant createdAt
) {

    public static ParkingBookingResponse from(ParkingBooking booking, boolean due) {
        return new ParkingBookingResponse(
                booking.getId(),
                booking.getBookId(),
                booking.getPlateNo(),
                booking.getMonthlyRateMinor(),
                booking.getRenewalMonth(),
                booking.isActive(),
                due,
                booking.getEnteredBy(),
                booking.getCreatedAt()
        );
    }
}
