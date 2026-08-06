package com.sandook.ledger.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    List<Transfer> findAllByOrderByDateAscIdAsc();

    List<Transfer> findAllByFromBookIdOrToBookIdOrderByDateAscIdAsc(Long fromBookId, Long toBookId);

    List<Transfer> findAllByDateBetweenOrderByDateAscIdAsc(LocalDate from, LocalDate to);

    List<Transfer> findAllByFromBookIdOrToBookIdAndDateBetweenOrderByDateAscIdAsc(
            Long fromBookId, Long toBookId, LocalDate from, LocalDate to);
}
