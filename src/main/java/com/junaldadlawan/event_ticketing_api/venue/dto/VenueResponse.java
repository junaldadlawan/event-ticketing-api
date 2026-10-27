package com.junaldadlawan.event_ticketing_api.venue.dto;

import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.enums.VenueType;

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
        VenueType venueType,
        String country,
        String province,
        String city,
        String address,
        Double latitude,
        Double longitude,
        String description,
        Integer maxCapacity,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) implements Serializable {

    public static VenueResponse from(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getOrganizationId(),
                venue.getName(),
                venue.getVenueType(),
                venue.getCountry(),
                venue.getProvince(),
                venue.getCity(),
                venue.getAddress(),
                venue.getLatitude(),
                venue.getLongitude(),
                venue.getDescription(),
                venue.getMaxCapacity(),
                venue.getCreatedBy(),
                venue.getCreatedAt(),
                venue.getUpdatedBy(),
                venue.getUpdatedAt());
    }
}
