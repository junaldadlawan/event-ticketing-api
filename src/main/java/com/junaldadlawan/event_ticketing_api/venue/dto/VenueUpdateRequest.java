package com.junaldadlawan.event_ticketing_api.venue.dto;

import com.junaldadlawan.event_ticketing_api.common.dto.PresentOrNull;
import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.Optional;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.venue.entity.Venue}
 * partial update (owning organization's owner or organizer only). All fields
 * optional per {@code VenueUpdate} in openapi.yaml. For {@code name}, {@code address}, {@code latitude} and {@code longitude} a left-out or null
 * field is unchanged (an empty {@code address} clears it). For the detail fields a left-out field is unchanged and JSON null clears it. Strings are
 * trimmed and a blank one counts as null.
 */
public record VenueUpdateRequest(
        @Size(max = 255)
        @NoHtml
        String name,

        @Size(max = 500)
        @NoHtml
        String address,

        Double latitude,

        Double longitude,

        @JsonDeserialize(using = PresentOrNull.OfString.class)
        Optional<String> venueType,

        @JsonDeserialize(using = PresentOrNull.OfString.class)
        Optional<String> country,

        @JsonDeserialize(using = PresentOrNull.OfString.class)
        Optional<@Size(max = 100) String> province,

        @JsonDeserialize(using = PresentOrNull.OfString.class)
        Optional<@Size(max = 100) String> city,

        @JsonDeserialize(using = PresentOrNull.OfString.class)
        Optional<@Size(max = 2000) String> description,

        @JsonDeserialize(using = PresentOrNull.OfInteger.class)
        Optional<@Min(1) Integer> maxCapacity,

        @JsonDeserialize(using = PresentOrNull.OfString.class)
        Optional<@Size(max = 500) String> coverImageUrl) {

    /** An update of only the original fields. */
    public VenueUpdateRequest(String name, String address, Double latitude, Double longitude) {
        this(name, address, latitude, longitude, null, null, null, null, null, null, null);
    }
}
