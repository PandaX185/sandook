package com.sandook.ledger.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only audit trail. Viewers can read it; writes happen inside the
 * domain services (never via this controller).
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditEntryResponse> list(@RequestParam(required = false) String entity,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(defaultValue = "100") int limit,
                                         @RequestParam(defaultValue = "0") int offset) {
        return auditService.list(entity, action, limit, offset);
    }
}
