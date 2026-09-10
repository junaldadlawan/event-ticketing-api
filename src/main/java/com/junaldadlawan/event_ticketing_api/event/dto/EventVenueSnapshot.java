package com.junaldadlawan.event_ticketing_api.event.dto;

import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;

import java.io.Serializable;
import java.util.UUID;

/**
 * Snapshot of the referenced {@link Venue} at read time, embedded in
 * {@link EventResponse}. Matches openapi.yaml's {@code Event.venue} —
 * {@code null} for a fully virtual event with no {@code venueId}.
 */
public record EventVenueSnapshot(
        UUID id,
        String name,
        String address,
        Double latitude,
        Double longitude) implements Serializable {

    public static EventVenueSnapshot from(Venue venue) {
        if (venue == null) {
            return null;
        }
        return new EventVenueSnapshot(
                venue.getId(),
                venue.getName(),
                venue.getAddress(),
                venue.getLatitude(),
                venue.getLongitude());
    }
}
