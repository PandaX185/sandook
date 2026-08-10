package com.sandook.ledger.parking;

import java.time.LocalDate;

/** In-app banner item: an active booking that needs attention. */
public record ParkingNotification(
        Long bookingId,
        String plateNo,
        String status, // OVERDUE | DUE_SOON
        LocalDate date
) {
}
