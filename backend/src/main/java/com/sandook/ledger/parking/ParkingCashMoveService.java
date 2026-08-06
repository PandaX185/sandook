package com.sandook.ledger.parking;

import com.sandook.ledger.audit.AuditService;
import com.sandook.ledger.book.Book;
import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.common.ConflictException;
import com.sandook.ledger.common.NotFoundException;
import com.sandook.ledger.user.User;
import com.sandook.ledger.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ParkingCashMoveService {

    private final ParkingCashMoveRepository moveRepository;
    private final ParkingSalaryPaymentRepository salaryPaymentRepository;
    private final ParkingBillRepository billRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ParkingCashMoveService(ParkingCashMoveRepository moveRepository,
                                  ParkingSalaryPaymentRepository salaryPaymentRepository,
                                  ParkingBillRepository billRepository,
                                  BookRepository bookRepository,
                                  UserRepository userRepository,
                                  AuditService auditService) {
        this.moveRepository = moveRepository;
        this.salaryPaymentRepository = salaryPaymentRepository;
        this.billRepository = billRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ParkingCashMoveResponse> list(Long bookId, LocalDate from, LocalDate to) {
        requireBook(bookId);
        List<ParkingCashMoveRow> rows = moveRepository.search(bookId, from, to);
        List<ParkingCashMoveResponse> responses = new ArrayList<>(rows.size());
        for (ParkingCashMoveRow row : rows) {
            Long moveId = row.getId();
            List<ParkingSalaryPayment> payments =
                    row.getType().equals(ParkingCashMoveType.SALARY.name())
                            ? salaryPaymentRepository.findAllByMoveIdOrderByIdAsc(moveId)
                            : List.of();
            responses.add(new ParkingCashMoveResponse(
                    moveId,
                    row.getBookId(),
                    row.getDate(),
                    ParkingCashMoveType.valueOf(row.getType()),
                    row.getAmountMinor(),
                    row.getDescription(),
                    payments.stream()
                            .map(p -> new ParkingCashMoveResponse.SalaryPaymentResponse(
                                    p.getId(), p.getPerson(), p.getAmountMinor()))
                            .toList(),
                    row.getBalanceMinor(),
                    row.getEnteredBy(),
                    null));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public ParkingCashStatement statement(Long bookId, LocalDate from, LocalDate to) {
        requireBook(bookId);

        // Bill cash totals per day.
        Map<LocalDate, Long> cashBills = new HashMap<>();
        for (ParkingBillDayTotal t : billRepository.cashTotalsByDay(bookId, from, to)) {
            cashBills.put(t.getDate(), t.getCashMinor());
        }

        // Move totals per day (OPENING positive, outbound negative, CLOSING excluded).
        Map<LocalDate, Long> moveNet = new HashMap<>();
        Map<LocalDate, Long> toShop = new HashMap<>();
        Map<LocalDate, Long> salaries = new HashMap<>();
        Map<LocalDate, Long> expenses = new HashMap<>();
        for (ParkingCashMove move : moveRepository.findAllByBookIdOrderByDateAscIdAsc(bookId)) {
            if (move.getType() == ParkingCashMoveType.CLOSING) {
                continue;
            }
            LocalDate date = move.getDate();
            moveNet.merge(date, signed(move), Long::sum);
            switch (move.getType()) {
                case TRANSFER_TO_SHOP -> toShop.merge(date, move.getAmountMinor(), Long::sum);
                case SALARY -> salaries.merge(date, move.getAmountMinor(), Long::sum);
                case EXPENSE -> expenses.merge(date, move.getAmountMinor(), Long::sum);
                default -> { }
            }
        }

        // Sorted, deduped union of bill days and move days.
        java.util.TreeSet<LocalDate> daySet = new java.util.TreeSet<>();
        daySet.addAll(cashBills.keySet());
        daySet.addAll(moveNet.keySet());
        List<LocalDate> days = new ArrayList<>(daySet);

        // Recorded CLOSING snapshots (for the sanity warning), first per date.
        Map<LocalDate, Long> recordedClosing = new HashMap<>();
        for (ParkingCashMove move : moveRepository.findAllByBookIdAndTypeOrderByDateAscIdAsc(
                bookId, ParkingCashMoveType.CLOSING)) {
            recordedClosing.putIfAbsent(move.getDate(), move.getAmountMinor());
        }

        List<ParkingCashStatement.DayRow> rows = new ArrayList<>(days.size());
        long opening = 0;
        for (LocalDate day : days) {
            long bills = cashBills.getOrDefault(day, 0L);
            long net = moveNet.getOrDefault(day, 0L);
            long closing = opening + bills + net;
            List<String> warnings = new ArrayList<>();
            Long recorded = recordedClosing.get(day);
            if (recorded != null && recorded != closing) {
                warnings.add("Recorded closing " + recorded + " does not match computed closing " + closing);
            }
            rows.add(new ParkingCashStatement.DayRow(
                    day,
                    opening,
                    bills,
                    toShop.getOrDefault(day, 0L),
                    salaries.getOrDefault(day, 0L),
                    expenses.getOrDefault(day, 0L),
                    toShop.getOrDefault(day, 0L) + salaries.getOrDefault(day, 0L) + expenses.getOrDefault(day, 0L),
                    closing,
                    warnings));
            opening = closing;
        }
        return new ParkingCashStatement(bookId, rows);
    }

    @Transactional(readOnly = true)
    public ParkingCashMoveResponse get(Long bookId, Long id) {
        requireBook(bookId);
        ParkingCashMove move = moveRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking cash move not found: book " + bookId + ", id " + id));
        return response(move);
    }

    @Transactional
    public ParkingCashMoveResponse create(Long bookId, ParkingCashMoveRequest request, String username) {
        requireBook(bookId);
        requireWritableType(request.type());
        ParkingCashMove move = new ParkingCashMove();
        move.setBookId(bookId);
        apply(move, request);
        move.setEnteredBy(userId(username));
        moveRepository.save(move);

        if (request.type() == ParkingCashMoveType.SALARY) {
            validateSalarySplit(request, move);
            saveSalaryPayments(move.getId(), request.salaryPayments());
        }
        ParkingCashMoveResponse response = response(move);
        auditService.record("CREATE", "parking_cash_move", move.getId(), null, response);
        return response;
    }

    @Transactional
    public ParkingCashMoveResponse update(Long bookId, Long id, ParkingCashMoveRequest request) {
        requireBook(bookId);
        ParkingCashMove move = moveRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking cash move not found: book " + bookId + ", id " + id));
        requireWritableType(move.getType());
        requireWritableType(request.type());

        tools.jackson.databind.JsonNode oldValue = auditService.toNode(move);
        salaryPaymentRepository.deleteByMoveId(move.getId());
        apply(move, request);
        moveRepository.save(move);
        if (request.type() == ParkingCashMoveType.SALARY) {
            validateSalarySplit(request, move);
            saveSalaryPayments(move.getId(), request.salaryPayments());
        }
        ParkingCashMoveResponse response = response(move);
        auditService.record("UPDATE", "parking_cash_move", move.getId(), oldValue, response);
        return response;
    }

    @Transactional
    public void delete(Long bookId, Long id) {
        requireBook(bookId);
        ParkingCashMove move = moveRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking cash move not found: book " + bookId + ", id " + id));
        requireWritableType(move.getType());
        auditService.record("DELETE", "parking_cash_move", move.getId(), auditService.toNode(move), null);
        salaryPaymentRepository.deleteByMoveId(move.getId());
        moveRepository.delete(move);
    }

    private ParkingCashMoveResponse response(ParkingCashMove move) {
        long balance = moveRepository.balanceBefore(move.getBookId(), move.getDate())
                + signed(move) + movesSameDayBefore(move);
        return ParkingCashMoveResponse.from(move,
                salaryPaymentRepository.findAllByMoveIdOrderByIdAsc(move.getId()), balance);
    }

    /** TRANSFER_TO_SHOP is auto-created by the transfer automation — no manual writes. */
    private void requireWritableType(ParkingCashMoveType type) {
        if (type == ParkingCashMoveType.TRANSFER_TO_SHOP) {
            throw new ConflictException("TRANSFER_TO_SHOP moves are created automatically by the transfer module");
        }
    }

    private void validateSalarySplit(ParkingCashMoveRequest request, ParkingCashMove move) {
        List<ParkingCashMoveRequest.SalaryPaymentRequest> payments = request.salaryPayments();
        if (payments == null || payments.isEmpty()) {
            throw new ConflictException("SALARY move requires at least one salary payment");
        }
        long sum = payments.stream().mapToLong(ParkingCashMoveRequest.SalaryPaymentRequest::amountMinor).sum();
        if (sum != move.getAmountMinor()) {
            throw new ConflictException("Salary payments sum " + sum
                    + " does not match move amount " + move.getAmountMinor());
        }
    }

    private void saveSalaryPayments(Long moveId,
                                    List<ParkingCashMoveRequest.SalaryPaymentRequest> payments) {
        for (ParkingCashMoveRequest.SalaryPaymentRequest p : payments) {
            ParkingSalaryPayment payment = new ParkingSalaryPayment();
            payment.setMoveId(moveId);
            payment.setPerson(p.person().trim());
            payment.setAmountMinor(p.amountMinor());
            salaryPaymentRepository.save(payment);
        }
    }

    private void apply(ParkingCashMove move, ParkingCashMoveRequest request) {
        move.setDate(request.date());
        move.setType(request.type());
        move.setAmountMinor(request.amountMinor());
        move.setDescription(request.description());
    }

    /** OPENING adds to cash, everything else (except CLOSING) takes away. */
    private long signed(ParkingCashMove move) {
        return move.getType() == ParkingCashMoveType.OPENING ? move.getAmountMinor() : -move.getAmountMinor();
    }

    /** Sum of same-day moves (by id) before this one — so the reported balance is exact. */
    private long movesSameDayBefore(ParkingCashMove move) {
        long sum = 0;
        for (ParkingCashMove m : moveRepository.findAllByBookIdAndDateOrderByIdAsc(move.getBookId(), move.getDate())) {
            if (m.getId().equals(move.getId())) {
                break;
            }
            if (m.getType() != ParkingCashMoveType.CLOSING) {
                sum += signed(m);
            }
        }
        return sum;
    }

    private Book requireBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("Book not found: " + bookId));
    }

    private Long userId(String username) {
        return userRepository.findByUsername(username).map(User::getId).orElse(null);
    }
}
