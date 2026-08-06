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
public class ParkingBookingService {

    private final ParkingBookingRepository bookingRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ParkingBookingService(ParkingBookingRepository bookingRepository,
                                 BookRepository bookRepository,
                                 UserRepository userRepository,
                                 AuditService auditService) {
        this.bookingRepository = bookingRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * List bookings. {@code dueWithinMonths} limits to active bookings whose
     * renewal month falls within the next N months (1 = current + next month).
     */
    @Transactional(readOnly = true)
    public List<ParkingBookingResponse> list(Long bookId, Boolean activeOnly, Integer dueWithinMonths) {
        requireBook(bookId);
        LocalDate cutoff = dueWithinMonths == null ? null
                : LocalDate.now().withDayOfMonth(1).plusMonths(dueWithinMonths);
        List<ParkingBooking> bookings;
        if (cutoff != null) {
            bookings = bookingRepository
                    .findAllByBookIdAndActiveTrueAndRenewalMonthLessThanEqualOrderByRenewalMonthAscIdAsc(
                            bookId, cutoff);
        } else if (Boolean.TRUE.equals(activeOnly)) {
            bookings = bookingRepository.findAllByBookIdAndActiveTrueOrderByRenewalMonthAscIdAsc(bookId);
        } else {
            bookings = bookingRepository.findAllByBookIdOrderByRenewalMonthAscIdAsc(bookId);
        }
        LocalDate dueCutoff = LocalDate.now().withDayOfMonth(1).plusMonths(2);
        return bookings.stream()
                .map(b -> ParkingBookingResponse.from(b, b.isActive() && !b.getRenewalMonth().isAfter(dueCutoff)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkingBookingResponse get(Long bookId, Long id) {
        requireBook(bookId);
        ParkingBooking booking = bookingRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Parking booking not found: book " + bookId + ", id " + id));
        return ParkingBookingResponse.from(booking, isDue(booking));
    }

    @Transactional
    public ParkingBookingResponse create(Long bookId, ParkingBookingRequest request, String username) {
        requireBook(bookId);
        ParkingBooking booking = new ParkingBooking();
        booking.setBookId(bookId);
        apply(booking, request);
        booking.setEnteredBy(userId(username));
        bookingRepository.save(booking);
        ParkingBookingResponse response = ParkingBookingResponse.from(booking, isDue(booking));
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
        ParkingBookingResponse response = ParkingBookingResponse.from(booking, isDue(booking));
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

    /** Due = active and renewal month is now or next month. */
    private boolean isDue(ParkingBooking booking) {
        LocalDate dueCutoff = LocalDate.now().withDayOfMonth(1).plusMonths(2);
        return booking.isActive() && !booking.getRenewalMonth().isAfter(dueCutoff);
    }

    private void apply(ParkingBooking booking, ParkingBookingRequest request) {
        booking.setPlateNo(request.plateNo().trim());
        booking.setMonthlyRateMinor(request.monthlyRateMinor());
        booking.setRenewalMonth(request.renewalMonth());
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
