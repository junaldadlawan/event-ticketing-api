package com.junaldadlawan.event_ticketing_api.venue.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.venue.entity.Venue}
 * creation. {@code address}/{@code latitude}/{@code longitude} are optional
 * — {@code address} is null for a purely virtual venue.
 */
public record VenueCreateRequest(
        @NotBlank
        @Size(max = 255)
        @NoHtml
        String name,

        @Size(max = 500)
        @NoHtml
        String address,

        Double latitude,

        Double longitude) {
}
