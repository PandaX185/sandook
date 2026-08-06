package com.sandook.ledger.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            LEFT JOIN FETCH a.user
            WHERE (CAST(:entity AS string) IS NULL OR a.entity = :entity)
              AND (CAST(:action AS string) IS NULL OR a.action = :action)
            ORDER BY a.id DESC
            """)
    List<AuditLog> search(@Param("entity") String entity,
                          @Param("action") String action,
                          Pageable pageable);
}
