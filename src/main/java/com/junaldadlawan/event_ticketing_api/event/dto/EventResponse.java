package com.junaldadlawan.event_ticketing_api.event.dto;

import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for {@link Event}
 */
public record EventResponse(
        UUID id,
        UUID organizationId,
        String title,
        String description,
        String category,
        EventVenueSnapshot venue,
        Instant startAt,
        Instant endAt,
        String timezone,
        List<String> images,
        EventStatus status,
        String ticketPrefix,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) implements Serializable {

    /**
     * Building the venue snapshot requires a repository lookup, so
     * {@code from} takes the already-resolved {@link Venue} (or
     * {@code null} for a virtual event) rather than looking it up itself —
     * repository access doesn't belong inside a DTO record.
     */
    public static EventResponse from(Event event, Venue venue) {
        return new EventResponse(event.getId(),
                event.getOrganizationId(),
                event.getTitle(),
                event.getDescription(),
                event.getCategory(),
                EventVenueSnapshot.from(venue),
                event.getStartAt(),
                event.getEndAt(),
                event.getTimezone(),
                event.getImages(),
                event.getStatus(),
                event.getTicketPrefix(),
                event.getCreatedBy(),
                event.getCreatedAt(),
                event.getUpdatedBy(),
                event.getUpdatedAt()
        );
    }

    /** Convenience overload for a fully virtual event (no venueId at all). */
    public static EventResponse from(Event event) {
        return from(event, null);
    }
}
