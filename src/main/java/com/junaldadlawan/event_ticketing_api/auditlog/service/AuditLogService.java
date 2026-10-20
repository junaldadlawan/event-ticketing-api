package com.junaldadlawan.event_ticketing_api.auditlog.service;

import com.junaldadlawan.event_ticketing_api.auditlog.dto.AuditLogEntryResponse;
import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface AuditLogService {

    /** Write side - called by other services right after the sensitive action's own save. Never throws. */
    void record(UUID actorId, String action, String targetType, UUID targetId);

    /**
     * Same, for an action about an event or something of it (BR-EVENT-006): it also shows in that event's activity
     * history. {@code actorId} null = the system acted on its own.
     */
    void record(UUID actorId, String action, String targetType, UUID targetId, UUID eventId);

    /** With {@code details} (built by {@link AuditDetails}; null = none) - what changed, or the item's name. */
    void record(UUID actorId, String action, String targetType, UUID targetId, UUID eventId, Map<String, Object> details);

    /** Same, by the signed-in caller (resolved here, so callers' own access checks stay untouched). */
    void recordByCurrentUser(String action, String targetType, UUID targetId, UUID eventId);

    void recordByCurrentUser(String action, String targetType, UUID targetId, UUID eventId, Map<String, Object> details);

    /** {@code GET /events/{eventId}/activity}, newest first - the event's owner/organizer, or an admin. */
    Page<AuditLogEntryResponse> listForEvent(UUID eventId, Pageable pageable);

    /** {@code GET /audit-log} - admin only. */
    Page<AuditLogEntry> list(UUID actorIdFilter, Pageable pageable);
}
