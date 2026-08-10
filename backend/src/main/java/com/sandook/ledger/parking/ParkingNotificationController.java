package com.sandook.ledger.parking;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/books/{bookId}/parking")
public class ParkingNotificationController {

    private final ParkingBookingService bookingService;

    public ParkingNotificationController(ParkingBookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** In-app banners: bookings overdue or due within the next 7 days. */
    @GetMapping("/notifications")
    public List<ParkingNotification> notifications(@PathVariable Long bookId) {
        return bookingService.notifications(bookId);
    }
}
