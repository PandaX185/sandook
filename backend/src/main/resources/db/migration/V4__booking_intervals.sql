-- Bookings upgrade: intervals, status, pay button.
-- Existing rows: renewal_month → next_due_date, MONTHLY interval, paid_through NULL
-- (they show DUE/OVERDUE until paid — honest).

ALTER TABLE parking_bookings RENAME COLUMN renewal_month TO next_due_date;

ALTER TABLE parking_bookings
    ADD COLUMN interval_type VARCHAR(20) NOT NULL DEFAULT 'MONTHLY'
        CHECK (interval_type IN ('MONTHLY', 'THREE_MONTHS', 'SIX_MONTHS', 'CUSTOM'));

ALTER TABLE parking_bookings ADD COLUMN interval_months INT;

ALTER TABLE parking_bookings ADD COLUMN paid_through_date DATE;

ALTER TABLE parking_bills
    ADD COLUMN booking_id BIGINT REFERENCES parking_bookings(id) ON DELETE SET NULL;

CREATE INDEX idx_parking_bills_booking ON parking_bills(booking_id);
