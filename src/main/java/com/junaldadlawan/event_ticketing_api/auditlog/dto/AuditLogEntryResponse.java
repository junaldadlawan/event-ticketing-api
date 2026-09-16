package com.junaldadlawan.event_ticketing_api.auditlog.dto;

import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** DTO for {@link AuditLogEntry}. */
public record AuditLogEntryResponse(
        UUID id,
        UUID actorId,
        String action,
        String targetType,
        UUID targetId,
        Instant createdAt) implements Serializable {

    public static AuditLogEntryResponse from(AuditLogEntry entry) {
        return new AuditLogEntryResponse(
                entry.getId(),
                entry.getActorId(),
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getCreatedAt());
    }
}
