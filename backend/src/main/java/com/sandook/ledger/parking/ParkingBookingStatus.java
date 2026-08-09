package com.sandook.ledger.parking;

/** Computed payment state of a parking booking (derived in the service, never stored). */
public enum ParkingBookingStatus {

    /** Booking is inactive — no payment expected. */
    INACTIVE,

    /** Today is before the last day of the paid-through period (or not yet due). */
    PAID,

    /** Today IS the last day of the paid-through period — pay now. */
    DUE,

    /** Today is past the last day of the paid-through period (or due date passed unpaid). */
    OVERDUE
}
