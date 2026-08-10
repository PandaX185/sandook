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
import java.util.Comparator;
import java.util.List;

@Service
public class ParkingBookingService {

    private final ParkingBookingRepository bookingRepository;
    private final ParkingBillRepository billRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ParkingBookingService(ParkingBookingRepository bookingRepository,
                                 ParkingBillRepository billRepository,
                                 BookRepository bookRepository,
                                 UserRepository userRepository,
                                 AuditService auditService) {
        this.bookingRepository = bookingRepository;
        this.billRepository = billRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /** List bookings, optionally restricted to active ones and/or a computed status. */
    @Transactional(readOnly = true)
    public List<ParkingBookingResponse> list(Long bookId, Boolean activeOnly, ParkingBookingStatus status) {
        requireBook(bookId);
        List<ParkingBooking> bookings = Boolean.TRUE.equals(activeOnly)
                ? bookingRepository.findAllByBookIdAndActiveTrueOrderByNextDueDateAscIdAsc(bookId)
                : bookingRepository.findAllByBookIdOrderByNextDueDateAscIdAsc(bookId);
        LocalDate today = LocalDate.now();
        return bookings.stream()
                .filter(b -> status == null || statusOf(b, today) == status)
                .map(b -> ParkingBookingResponse.from(b, statusOf(b, today)))
                .toList();
    }

    /** In-app banner items: active bookings overdue or due within the next 7 days. */
    @Transactional(readOnly = true)
    public List<ParkingNotification> notifications(Long bookId) {
        requireBook(bookId);
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(7);
        return bookingRepository.findAllByBookIdOrderByNextDueDateAscIdAsc(bookId).stream()
                .filter(ParkingBooking::isActive)
                .map(booking -> {
                    LocalDate ref = booking.getPaidThroughDate() != null
                            ? booking.getPaidThroughDate()
                            : booking.getNextDueDate();
                    String status = ref.isBefore(today) ? "OVERDUE" : "DUE_SOON";
                    return new ParkingNotification(booking.getId(), booking.getPlateNo(), status, ref);
                })
                .filter(n -> n.date().isBefore(today) || !n.date().isAfter(horizon))
                .sorted(Comparator.comparing(ParkingNotification::date))
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkingBookingResponse get(Long bookId, Long id) {
        requireBook(bookId);
        ParkingBooking booking = bookingRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking booking not found: book " + bookId + ", id " + id));
        return ParkingBookingResponse.from(booking, statusOf(booking, LocalDate.now()));
    }

    /** Payment history for one booking, oldest first. */
    @Transactional(readOnly = true)
    public List<ParkingBillResponse> payments(Long bookId, Long id) {
        requireBook(bookId);
        bookingRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking booking not found: book " + bookId + ", id " + id));
        return billRepository.findByBookingIdOrderByBilledAtAscIdAsc(id).stream()
                .map(ParkingBillResponse::from)
                .toList();
    }

    @Transactional
    public ParkingBookingResponse create(Long bookId, ParkingBookingRequest request, String username) {
        requireBook(bookId);
        ParkingBooking booking = new ParkingBooking();
        booking.setBookId(bookId);
        apply(booking, request);
        booking.setEnteredBy(userId(username));
        bookingRepository.save(booking);
        ParkingBookingResponse response = ParkingBookingResponse.from(booking, statusOf(booking, LocalDate.now()));
        auditService.record("CREATE", "parking_booking", booking.getId(), null, response);
        return response;
    }

    @Transactional
    public ParkingBookingResponse update(Long bookId, Long id, ParkingBookingRequest request) {
        requireBook(bookId);
        ParkingBooking booking = bookingRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking booking not found: book " + bookId + ", id " + id));
        tools.jackson.databind.JsonNode oldValue = auditService.toNode(booking);
        apply(booking, request);
        bookingRepository.save(booking);
        ParkingBookingResponse response = ParkingBookingResponse.from(booking, statusOf(booking, LocalDate.now()));
        auditService.record("UPDATE", "parking_booking", booking.getId(), oldValue, response);
        return response;
    }

    /**
     * Pay a booking: creates ONE bill for the full amount (rate × months, editable
     * for discounts), then advances the paid-through and next-due dates.
     */
    @Transactional
    public ParkingBookingResponse pay(Long bookId, Long id, ParkingBookingPayRequest request, String username) {
        requireBook(bookId);
        ParkingBooking booking = bookingRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking booking not found: book " + bookId + ", id " + id));
        int months = booking.getIntervalType().months(booking.getIntervalMonths());
        LocalDate billedAt = request.paidAt() != null ? request.paidAt() : LocalDate.now();

        ParkingBill bill = new ParkingBill();
        bill.setBookId(bookId);
        bill.setPlateNo(booking.getPlateNo());
        bill.setAmountMinor(request.amountMinor());
        bill.setPaymentMethod(request.paymentMethod());
        bill.setBilledAt(billedAt);
        bill.setBookingId(booking.getId());
        bill.setEnteredBy(userId(username));
        billRepository.save(bill);
        ParkingBillResponse billResponse = ParkingBillResponse.from(bill);
        auditService.record("CREATE", "parking_bill", bill.getId(), null, billResponse);

        tools.jackson.databind.JsonNode oldValue = auditService.toNode(booking);
        LocalDate nextDue = booking.getNextDueDate();
        booking.setPaidThroughDate(nextDue.plusMonths(months).minusDays(1));
        booking.setNextDueDate(nextDue.plusMonths(months));
        bookingRepository.save(booking);
        ParkingBookingResponse response = ParkingBookingResponse.from(booking, statusOf(booking, LocalDate.now()));
        auditService.record("UPDATE", "parking_booking", booking.getId(), oldValue, response);
        return response;
    }

    @Transactional
    public void delete(Long bookId, Long id) {
        requireBook(bookId);
        ParkingBooking booking = bookingRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking booking not found: book " + bookId + ", id " + id));
        auditService.record("DELETE", "parking_booking", booking.getId(), auditService.toNode(booking), null);
        bookingRepository.delete(booking);
    }

    /**
     * Status rules: INACTIVE if deactivated; never-paid bookings are DUE once
     * today ≥ next_due_date; paid bookings are PAID before the paid-through
     * period ends, DUE on its last day, OVERDUE after.
     */
    private ParkingBookingStatus statusOf(ParkingBooking booking, LocalDate today) {
        if (!booking.isActive()) {
            return ParkingBookingStatus.INACTIVE;
        }
        LocalDate paidThrough = booking.getPaidThroughDate();
        if (paidThrough == null) {
            return today.isBefore(booking.getNextDueDate())
                    ? ParkingBookingStatus.PAID
                    : ParkingBookingStatus.DUE;
        }
        if (today.isBefore(paidThrough)) {
            return ParkingBookingStatus.PAID;
        }
        if (today.isEqual(paidThrough)) {
            return ParkingBookingStatus.DUE;
        }
        return ParkingBookingStatus.OVERDUE;
    }

    private void apply(ParkingBooking booking, ParkingBookingRequest request) {
        booking.setPlateNo(request.plateNo().trim());
        booking.setMonthlyRateMinor(request.monthlyRateMinor());
        booking.setIntervalType(request.intervalType());
        booking.setIntervalMonths(request.intervalType() == ParkingBookingInterval.CUSTOM
                ? request.intervalMonths()
                : null);
        booking.setNextDueDate(request.nextDueDate());
        if (request.active() != null) {
            booking.setActive(request.active());
        }
    }

    private Book requireBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("Book not found: " + bookId));
    }

    private Long userId(String username) {
        return userRepository.findByUsername(username).map(User::getId).orElse(null);
    }
}
