package com.sandook.ledger.parking;

import java.time.Instant;
import java.time.LocalDate;

/** Parking bill with its payment method. Money in minor units (fils). */
public record ParkingBillResponse(
        Long id,
        Long bookId,
        String plateNo,
        long amountMinor,
        PaymentMethod paymentMethod,
        LocalDate billedAt,
        Long enteredBy,
        Instant createdAt
) {

    public static ParkingBillResponse from(ParkingBill bill) {
        return new ParkingBillResponse(
                bill.getId(),
                bill.getBookId(),
                bill.getPlateNo(),
                bill.getAmountMinor(),
                bill.getPaymentMethod(),
                bill.getBilledAt(),
                bill.getEnteredBy(),
                bill.getCreatedAt()
        );
    }
}
