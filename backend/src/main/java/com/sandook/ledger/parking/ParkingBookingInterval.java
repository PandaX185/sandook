package com.sandook.ledger.parking;

/** How often a parking booking is billed. CUSTOM uses the booking's intervalMonths (1–24). */
public enum ParkingBookingInterval {

    MONTHLY(1),
    THREE_MONTHS(3),
    SIX_MONTHS(6),
    CUSTOM(0);

    private final int defaultMonths;

    ParkingBookingInterval(int defaultMonths) {
        this.defaultMonths = defaultMonths;
    }

    /** Months covered by one payment for this interval. CUSTOM delegates to the booking's months. */
    public int months(Integer customMonths) {
        return this == CUSTOM ? (customMonths == null ? 0 : customMonths) : defaultMonths;
    }
}
