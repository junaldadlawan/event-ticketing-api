package com.junaldadlawan.event_ticketing_api.event.service;

import com.junaldadlawan.event_ticketing_api.event.dto.EventRequest;
import com.junaldadlawan.event_ticketing_api.event.dto.EventUpdateRequest;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface EventService {
    Event createEvent(EventRequest request);
    Page<Event> listEvents(String category, String keyword, Instant from, Instant to, Pageable pageable);
    Event getEvent(UUID eventId);
    Event updateEvent(UUID eventId, EventUpdateRequest request);
    Event publishEvent(UUID eventId);
    Event cancelEvent(UUID eventId);
    void delete(UUID id);

    /**
     * Resolves the {@link Venue} snapshot for an event's {@code venueId}
     * (or {@code null} if the event has none / the venue can no longer be
     * found), for {@code EventResponse.from(event, venue)} to embed.
     * Repository access is kept here rather than in the controller or DTO.
     */
    Venue resolveVenue(UUID venueId);
}
