package com.junaldadlawan.event_ticketing_api.auditlog.dto;

import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link AuditLogEntry}. {@code actorName} is only filled in by the per-event activity history (null
 * elsewhere); a null {@code actorId} means the system acted on its own.
 */
public record AuditLogEntryResponse(
        UUID id,
        UUID actorId,
        String actorName,
        String action,
        String targetType,
        UUID targetId,
        UUID eventId,
        Instant createdAt) implements Serializable {

    public static AuditLogEntryResponse from(AuditLogEntry entry) {
        return from(entry, null);
    }

    public static AuditLogEntryResponse from(AuditLogEntry entry, String actorName) {
        return new AuditLogEntryResponse(
                entry.getId(),
                entry.getActorId(),
                actorName,
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getEventId(),
                entry.getCreatedAt());
    }
}
