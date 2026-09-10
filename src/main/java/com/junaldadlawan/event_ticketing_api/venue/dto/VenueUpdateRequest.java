package com.junaldadlawan.event_ticketing_api.venue.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.Size;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.venue.entity.Venue}
 * partial update (owning organization's owner or organizer only). All fields
 * optional per {@code VenueUpdate} in openapi.yaml.
 */
public record VenueUpdateRequest(
        @Size(max = 255)
        @NoHtml
        String name,

        @Size(max = 500)
        @NoHtml
        String address,

        Double latitude,

        Double longitude) {
}
