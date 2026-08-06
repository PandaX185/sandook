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
@RequestMapping("/api/v1/books/{bookId}/parking/cash-moves")
public class ParkingCashMoveController {

    private final ParkingCashMoveService moveService;

    public ParkingCashMoveController(ParkingCashMoveService moveService) {
        this.moveService = moveService;
    }

    @GetMapping
    public List<ParkingCashMoveResponse> list(@PathVariable Long bookId,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return moveService.list(bookId, from, to);
    }

    @GetMapping("/statement")
    public ParkingCashStatement statement(@PathVariable Long bookId,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return moveService.statement(bookId, from, to);
    }

    @PostMapping
    @PreAuthorize("hasRole('EDITOR')")
    public ParkingCashMoveResponse create(@PathVariable Long bookId,
                                          @Valid @RequestBody ParkingCashMoveRequest request,
                                          Authentication authentication) {
        return moveService.create(bookId, request, authentication.getName());
    }

    @GetMapping("/{id}")
    public ParkingCashMoveResponse get(@PathVariable Long bookId, @PathVariable Long id) {
        return moveService.get(bookId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    public ParkingCashMoveResponse update(@PathVariable Long bookId,
                                          @PathVariable Long id,
                                          @Valid @RequestBody ParkingCashMoveRequest request) {
        return moveService.update(bookId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long bookId, @PathVariable Long id) {
        moveService.delete(bookId, id);
    }
}
