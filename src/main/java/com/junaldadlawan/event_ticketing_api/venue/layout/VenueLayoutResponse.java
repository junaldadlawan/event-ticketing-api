package com.junaldadlawan.event_ticketing_api.venue.layout;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/** {@code GET/PUT /venues/{venueId}/layout}: the layout and its {@code version} (0 = nothing saved yet, so an empty plan). */
public record VenueLayoutResponse(UUID venueId, int version, double width, double height, List<VenueLayout.Section> sections, List<VenueLayout.Shape> shapes)
        implements Serializable {

    public static VenueLayoutResponse of(UUID venueId, int version, VenueLayout layout) {
        return new VenueLayoutResponse(venueId, version, layout.width(), layout.height(), layout.sections(), layout.shapes());
    }
}
