package com.sandook.ledger.parking;

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

    public ParkingBillService(ParkingBillRepository billRepository,
                              BookRepository bookRepository,
                              UserRepository userRepository) {
        this.billRepository = billRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ParkingBillResponse> list(Long bookId, LocalDate from, LocalDate to, String plate) {
        requireBook(bookId);
        return billRepository.search(bookId, from, to, plate).stream()
                .map(row -> new ParkingBillResponse(
                        row.getId(),
                        row.getBookId(),
                        row.getPlateNo(),
                        row.getAmountMinor(),
                        PaymentMethod.valueOf(row.getPaymentMethod()),
                        row.getBilledAt(),
                        row.getEnteredBy(),
                        null))
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkingBillSummary summary(Long bookId, LocalDate from, LocalDate to) {
        requireBook(bookId);
        return ParkingBillSummary.from(bookId, from, to, billRepository.summarize(bookId, from, to));
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
        return ParkingBillResponse.from(bill);
    }

    @Transactional
    public ParkingBillResponse update(Long bookId, Long id, ParkingBillRequest request) {
        requireBook(bookId);
        ParkingBill bill = billRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking bill not found: book " + bookId + ", id " + id));
        apply(bill, request);
        billRepository.save(bill);
        return ParkingBillResponse.from(bill);
    }

    @Transactional
    public void delete(Long bookId, Long id) {
        requireBook(bookId);
        ParkingBill bill = billRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking bill not found: book " + bookId + ", id " + id));
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
