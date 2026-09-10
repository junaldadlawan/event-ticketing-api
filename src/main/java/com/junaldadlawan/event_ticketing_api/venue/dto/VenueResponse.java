package com.junaldadlawan.event_ticketing_api.venue.dto;

import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link Venue}
 */
public record VenueResponse(
        UUID id,
        UUID organizationId,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) implements Serializable {

    public static VenueResponse from(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getOrganizationId(),
                venue.getName(),
                venue.getAddress(),
                venue.getLatitude(),
                venue.getLongitude(),
                venue.getCreatedBy(),
                venue.getCreatedAt(),
                venue.getUpdatedBy(),
                venue.getUpdatedAt());
    }
}
