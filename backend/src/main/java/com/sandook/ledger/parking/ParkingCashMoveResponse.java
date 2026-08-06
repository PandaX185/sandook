package com.sandook.ledger.parking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Cash move with its salary split (SALARY only) and running balance. */
public record ParkingCashMoveResponse(
        Long id,
        Long bookId,
        LocalDate date,
        ParkingCashMoveType type,
        long amountMinor,
        String description,
        List<SalaryPaymentResponse> salaryPayments,
        long balanceMinor,
        Long enteredBy,
        Instant createdAt
) {

    public record SalaryPaymentResponse(Long id, String person, long amountMinor) {
    }

    public static ParkingCashMoveResponse from(ParkingCashMove move, List<ParkingSalaryPayment> payments,
                                               long balanceMinor) {
        List<SalaryPaymentResponse> splits = payments == null ? List.of() : payments.stream()
                .map(p -> new SalaryPaymentResponse(p.getId(), p.getPerson(), p.getAmountMinor()))
                .toList();
        return new ParkingCashMoveResponse(
                move.getId(),
                move.getBookId(),
                move.getDate(),
                move.getType(),
                move.getAmountMinor(),
                move.getDescription(),
                splits,
                balanceMinor,
                move.getEnteredBy(),
                move.getCreatedAt()
        );
    }
}
