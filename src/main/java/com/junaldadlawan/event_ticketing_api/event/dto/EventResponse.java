package com.junaldadlawan.event_ticketing_api.event.dto;

import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.event.entity.Event}
 */
public record EventResponse(
        UUID id,
        UUID organizationId,
        String title,
        String description,
        String category,
        int venue,
        Instant startAt,
        Instant endAt,
        String timezone,
        byte[] image,
        EventStatus status,
        String ticketPrefix,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) implements Serializable {
    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(),
                event.getOrganizationId(),
                event.getTitle(),
                event.getDescription(),
                event.getCategory(),
                event.getVenue(),
                event.getStartAt(),
                event.getEndAt(),
                event.getTimezone(),
                event.getImage(),
                event.getStatus(),
                event.getTicketPrefix(),
                event.getCreatedBy(),
                event.getCreatedAt(),
                event.getUpdatedBy(),
                event.getUpdatedAt()
        );
    }
}