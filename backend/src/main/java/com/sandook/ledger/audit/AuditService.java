package com.sandook.ledger.audit;

import com.sandook.ledger.user.User;
import com.sandook.ledger.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Appends audit rows for every write. Runs in the same transaction as the
 * business write, so a rolled-back write never leaves an audit entry.
 * The actor is resolved from the security context (anonymous/system writes
 * fall back to {@code null}).
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository,
                        UserRepository userRepository,
                        ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /** Appends a CREATE/UPDATE/DELETE entry. Null values are stored as SQL NULL. */
    @Transactional
    public void record(String action, String entity, Long entityId,
                       Object oldValue, Object newValue) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntity(entity);
        log.setEntityId(entityId);
        log.setUserId(currentUserId());
        log.setOldValue(toNode(oldValue));
        log.setNewValue(toNode(newValue));
        auditLogRepository.save(log);
    }

    /** Serializes an object to a JSON node for JSONB storage. */
    public JsonNode toNode(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.valueToTree(value);
    }

    @Transactional(readOnly = true)
    public List<AuditEntryResponse> list(String entity, String action, int limit, int offset) {
        int capped = Math.min(Math.max(limit, 1), 500);
        int skip = Math.max(offset, 0);
        return auditLogRepository.search(entity, action, PageRequest.of(skip / capped, capped))
                .stream()
                .map(AuditEntryResponse::from)
                .toList();
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).map(User::getId).orElse(null);
    }
}
