package com.sandook.ledger.parking;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/books/{bookId}/parking/bills")
public class ParkingBillController {

    private final ParkingBillService billService;

    public ParkingBillController(ParkingBillService billService) {
        this.billService = billService;
    }

    @GetMapping
    public List<ParkingBillResponse> list(@PathVariable Long bookId,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) String plate,
                                          @RequestParam(required = false) String paymentMethod) {
        return billService.list(bookId, from, to, plate, paymentMethod);
    }

    @GetMapping("/summary")
    public ParkingBillSummary summary(@PathVariable Long bookId,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) String paymentMethod) {
        return billService.summary(bookId, from, to, paymentMethod);
    }

    @PostMapping
    @PreAuthorize("hasRole('EDITOR')")
    public ParkingBillResponse create(@PathVariable Long bookId,
                                      @Valid @RequestBody ParkingBillRequest request,
                                      Authentication authentication) {
        return billService.create(bookId, request, authentication.getName());
    }

    @GetMapping("/{id}")
    public ParkingBillResponse get(@PathVariable Long bookId, @PathVariable Long id) {
        return billService.get(bookId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    public ParkingBillResponse update(@PathVariable Long bookId,
                                      @PathVariable Long id,
                                      @Valid @RequestBody ParkingBillRequest request) {
        return billService.update(bookId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long bookId, @PathVariable Long id) {
        billService.delete(bookId, id);
    }
}
