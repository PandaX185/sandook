package com.sandook.ledger.pettycash;

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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/books/{bookId}/petty-cash")
public class PettyCashController {

    private final PettyCashService pettyCashService;

    public PettyCashController(PettyCashService pettyCashService) {
        this.pettyCashService = pettyCashService;
    }

    @GetMapping("/transactions")
    public List<PettyCashTransactionResponse> list(@PathVariable Long bookId) {
        return pettyCashService.list(bookId);
    }

    @GetMapping("/balance")
    public Map<String, Object> balance(@PathVariable Long bookId,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        long balance = asOf == null ? pettyCashService.balance(bookId) : pettyCashService.balanceAsOf(bookId, asOf);
        return Map.of("bookId", bookId, "balanceMinor", balance);
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasRole('EDITOR')")
    public PettyCashTransactionResponse create(@PathVariable Long bookId,
                                               @Valid @RequestBody PettyCashTransactionRequest request,
                                               Authentication authentication) {
        return pettyCashService.create(bookId, request, authentication.getName());
    }

    @PutMapping("/transactions/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    public PettyCashTransactionResponse update(@PathVariable Long bookId,
                                               @PathVariable Long id,
                                               @Valid @RequestBody PettyCashTransactionRequest request) {
        return pettyCashService.update(bookId, id, request);
    }

    @DeleteMapping("/transactions/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long bookId, @PathVariable Long id) {
        pettyCashService.delete(bookId, id);
    }
}
