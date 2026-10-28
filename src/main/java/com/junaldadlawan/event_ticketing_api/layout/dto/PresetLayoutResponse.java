package com.junaldadlawan.event_ticketing_api.layout.dto;

import com.junaldadlawan.event_ticketing_api.layout.entity.PresetLayout;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One preset layout, in full. */
public record PresetLayoutResponse(UUID id, String name, String description, boolean active, int version, int seatCount, double width, double height,
                                   List<VenueLayout.Section> sections, List<VenueLayout.Shape> shapes, Instant createdAt, Instant updatedAt)
        implements Serializable {

    public static PresetLayoutResponse of(PresetLayout entity, VenueLayout layout) {
        return new PresetLayoutResponse(entity.getId(), entity.getName(), entity.getDescription(), entity.isActive(), entity.getVersion(),
                entity.getSeatCount(), entity.getWidth(), entity.getHeight(), layout.sections(), layout.shapes(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
