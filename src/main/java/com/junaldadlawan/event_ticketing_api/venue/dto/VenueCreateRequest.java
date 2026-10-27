package com.junaldadlawan.event_ticketing_api.venue.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.venue.entity.Venue}
 * creation. Everything but {@code name} is optional - a purely virtual venue has no {@code address}, position or capacity. Strings are
 * trimmed and a blank one is stored as null. {@code venueType} is a {@link com.junaldadlawan.event_ticketing_api.venue.enums.VenueType} name and
 * {@code country} an ISO 3166-1 alpha-2 code; both are checked in the service so the answer says what is wrong. {@code coverImageUrl} must be the
 * URL of an image uploaded through the upload endpoint (folder "venues").
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

        Double longitude,

        String venueType,

        String country,

        @Size(max = 100)
        @NoHtml
        String province,

        @Size(max = 100)
        @NoHtml
        String city,

        @Size(max = 2000)
        @NoHtml
        String description,

        /** People the venue holds; may be more than the seats of its seat layout. */
        @Min(1)
        Integer maxCapacity,

        @Size(max = 500)
        String coverImageUrl) {

    /** A create with only the original fields. */
    public VenueCreateRequest(String name, String address, Double latitude, Double longitude) {
        this(name, address, latitude, longitude, null, null, null, null, null, null, null);
    }
}
