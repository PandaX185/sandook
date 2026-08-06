package com.sandook.ledger.audit;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/** Audit entry as returned to the client. */
public record AuditEntryResponse(
        Long id,
        String username,
        String action,
        String entity,
        Long entityId,
        JsonNode oldValue,
        JsonNode newValue,
        Instant createdAt
) {

    public static AuditEntryResponse from(AuditLog log) {
        return new AuditEntryResponse(
                log.getId(),
                log.getUser() == null ? null : log.getUser().getUsername(),
                log.getAction(),
                log.getEntity(),
                log.getEntityId(),
                log.getOldValue(),
                log.getNewValue(),
                log.getCreatedAt()
        );
    }
}
