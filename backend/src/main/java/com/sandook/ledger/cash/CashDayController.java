package com.sandook.ledger.cash;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/books/{bookId}/cash-days")
public class CashDayController {

    private final CashDayService cashDayService;

    public CashDayController(CashDayService cashDayService) {
        this.cashDayService = cashDayService;
    }

    @GetMapping
    public List<CashDayResponse> list(@PathVariable Long bookId) {
        return cashDayService.list(bookId);
    }

    @GetMapping("/{id}")
    public CashDayResponse get(@PathVariable Long bookId, @PathVariable Long id) {
        return cashDayService.get(bookId, id);
    }

    @PostMapping
    @PreAuthorize("hasRole('EDITOR')")
    public CashDayResponse create(@PathVariable Long bookId,
                                  @Valid @RequestBody CashDayRequest request,
                                  Authentication authentication) {
        return cashDayService.create(bookId, request, authentication.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    public CashDayResponse update(@PathVariable Long bookId,
                                  @PathVariable Long id,
                                  @Valid @RequestBody CashDayRequest request) {
        return cashDayService.update(bookId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long bookId, @PathVariable Long id) {
        cashDayService.delete(bookId, id);
    }
}
