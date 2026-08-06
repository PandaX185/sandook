package com.sandook.ledger.parking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParkingSalaryPaymentRepository extends JpaRepository<ParkingSalaryPayment, Long> {

    List<ParkingSalaryPayment> findAllByMoveIdOrderByIdAsc(Long moveId);

    void deleteByMoveId(Long moveId);
}
