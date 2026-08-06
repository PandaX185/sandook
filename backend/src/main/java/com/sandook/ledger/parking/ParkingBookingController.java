package com.sandook.ledger.parking;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/books/{bookId}/parking/bookings")
public class ParkingBookingController {

    private final ParkingBookingService bookingService;

    public ParkingBookingController(ParkingBookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public List<ParkingBookingResponse> list(@PathVariable Long bookId,
                                             @RequestParam(required = false) Boolean active,
                                             @RequestParam(required = false) Integer dueWithinMonths) {
        return bookingService.list(bookId, active, dueWithinMonths);
    }

    @PostMapping
    @PreAuthorize("hasRole('EDITOR')")
    public ParkingBookingResponse create(@PathVariable Long bookId,
                                         @Valid @RequestBody ParkingBookingRequest request,
                                         Authentication authentication) {
        return bookingService.create(bookId, request, authentication.getName());
    }

    @GetMapping("/{id}")
    public ParkingBookingResponse get(@PathVariable Long bookId, @PathVariable Long id) {
        return bookingService.get(bookId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    public ParkingBookingResponse update(@PathVariable Long bookId,
                                         @PathVariable Long id,
                                         @Valid @RequestBody ParkingBookingRequest request) {
        return bookingService.update(bookId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long bookId, @PathVariable Long id) {
        bookingService.delete(bookId, id);
    }
}
