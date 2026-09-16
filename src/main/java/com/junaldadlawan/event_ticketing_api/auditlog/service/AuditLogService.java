package com.junaldadlawan.event_ticketing_api.auditlog.service;

import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AuditLogService {

    /** Write side - called by other services right after the sensitive action's own save. Never throws. */
    void record(UUID actorId, String action, String targetType, UUID targetId);

    /** {@code GET /audit-log} - admin only. */
    Page<AuditLogEntry> list(UUID actorIdFilter, Pageable pageable);
}
