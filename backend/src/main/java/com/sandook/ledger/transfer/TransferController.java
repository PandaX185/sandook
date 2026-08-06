package com.sandook.ledger.transfer;

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
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping
    public List<TransferResponse> list(@RequestParam(required = false) Long bookId,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return transferService.list(bookId, from, to);
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable Long id) {
        return transferService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('EDITOR')")
    public TransferResponse create(@Valid @RequestBody TransferRequest request,
                                   Authentication authentication) {
        return transferService.create(request, authentication.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    public TransferResponse update(@PathVariable Long id,
                                   @Valid @RequestBody TransferUpdateRequest request) {
        return transferService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        transferService.delete(id);
    }
}
