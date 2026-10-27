package com.junaldadlawan.event_ticketing_api.layout.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

/** {@code POST /layout-presets} and {@code PUT /layout-presets/{presetId}} (admin). */
public record PresetLayoutRequest(
        @NotBlank
        @Size(max = 100)
        @NoHtml
        String name,

        @Size(max = 500)
        @NoHtml
        String description,

        /** Null = true on create, unchanged on replace. */
        Boolean active,

        Double width,
        Double height,
        List<VenueLayout.Section> sections,
        List<VenueLayout.Shape> shapes) implements Serializable {

    public VenueLayout layout() {
        return new VenueLayout(width, height, sections, shapes);
    }
}
