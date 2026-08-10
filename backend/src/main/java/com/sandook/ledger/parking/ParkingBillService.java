package com.sandook.ledger.parking;

import com.sandook.ledger.audit.AuditService;
import com.sandook.ledger.book.Book;
import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.common.NotFoundException;
import com.sandook.ledger.user.User;
import com.sandook.ledger.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ParkingBillService {

    private final ParkingBillRepository billRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ParkingBillService(ParkingBillRepository billRepository,
                              BookRepository bookRepository,
                              UserRepository userRepository,
                              AuditService auditService) {
        this.billRepository = billRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ParkingBillResponse> list(Long bookId, LocalDate from, LocalDate to, String plate,
                                          String paymentMethod) {
        requireBook(bookId);
        return billRepository.search(bookId, from, to, plate, paymentMethod).stream()
                .map(row -> new ParkingBillResponse(
                        row.getId(),
                        row.getBookId(),
                        row.getBookingId(),
                        row.getPlateNo(),
                        row.getAmountMinor(),
                        PaymentMethod.valueOf(row.getPaymentMethod()),
                        row.getBilledAt(),
                        row.getEnteredBy(),
                        null))
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkingBillSummary summary(Long bookId, LocalDate from, LocalDate to, String paymentMethod) {
        requireBook(bookId);
        return ParkingBillSummary.from(bookId, from, to,
                billRepository.summarize(bookId, from, to, paymentMethod));
    }

    @Transactional(readOnly = true)
    public ParkingBillResponse get(Long bookId, Long id) {
        requireBook(bookId);
        ParkingBill bill = billRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking bill not found: book " + bookId + ", id " + id));
        return ParkingBillResponse.from(bill);
    }

    @Transactional
    public ParkingBillResponse create(Long bookId, ParkingBillRequest request, String username) {
        requireBook(bookId);
        ParkingBill bill = new ParkingBill();
        bill.setBookId(bookId);
        apply(bill, request);
        bill.setEnteredBy(userId(username));
        billRepository.save(bill);
        ParkingBillResponse response = ParkingBillResponse.from(bill);
        auditService.record("CREATE", "parking_bill", bill.getId(), null, response);
        return response;
    }

    @Transactional
    public ParkingBillResponse update(Long bookId, Long id, ParkingBillRequest request) {
        requireBook(bookId);
        ParkingBill bill = billRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking bill not found: book " + bookId + ", id " + id));
        tools.jackson.databind.JsonNode oldValue = auditService.toNode(bill);
        apply(bill, request);
        billRepository.save(bill);
        ParkingBillResponse response = ParkingBillResponse.from(bill);
        auditService.record("UPDATE", "parking_bill", bill.getId(), oldValue, response);
        return response;
    }

    @Transactional
    public void delete(Long bookId, Long id) {
        requireBook(bookId);
        ParkingBill bill = billRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking bill not found: book " + bookId + ", id " + id));
        auditService.record("DELETE", "parking_bill", bill.getId(), auditService.toNode(bill), null);
        billRepository.delete(bill);
    }

    private void apply(ParkingBill bill, ParkingBillRequest request) {
        bill.setPlateNo(request.plateNo().trim());
        bill.setAmountMinor(request.amountMinor());
        bill.setPaymentMethod(request.paymentMethod());
        bill.setBilledAt(request.billedAt());
    }

    private Book requireBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("Book not found: " + bookId));
    }

    private Long userId(String username) {
        return userRepository.findByUsername(username).map(User::getId).orElse(null);
    }
}
