package com.junaldadlawan.event_ticketing_api.layout.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

/** {@code POST /organizations/{orgId}/layouts} and {@code PUT /layouts/{layoutId}}: the name and the whole design (checked by VenueLayoutValidator). */
public record SeatLayoutRequest(
        @NotBlank
        @Size(max = 100)
        @NoHtml
        String name,

        Double width,
        Double height,
        List<VenueLayout.Section> sections,
        List<VenueLayout.Shape> shapes) implements Serializable {

    public VenueLayout layout() {
        return new VenueLayout(width, height, sections, shapes);
    }
}
