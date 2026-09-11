package com.junaldadlawan.event_ticketing_api.waitlist.dto;

import com.junaldadlawan.event_ticketing_api.waitlist.entity.WaitlistEntry;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** DTO for {@link WaitlistEntry}, matching openapi.yaml's {@code WaitlistEntry} schema. */
public record WaitlistEntryResponse(
        UUID id,
        UUID eventId,
        UUID ticketTypeId,
        UUID userId,
        int position,
        Instant notifiedAt,
        Instant offerExpiresAt,
        Instant createdAt) implements Serializable {

    public static WaitlistEntryResponse from(WaitlistEntry entry) {
        return new WaitlistEntryResponse(
                entry.getId(),
                entry.getEventId(),
                entry.getTicketTypeId(),
                entry.getUserId(),
                entry.getPosition(),
                entry.getNotifiedAt(),
                entry.getOfferExpiresAt(),
                entry.getCreatedAt());
    }
}
