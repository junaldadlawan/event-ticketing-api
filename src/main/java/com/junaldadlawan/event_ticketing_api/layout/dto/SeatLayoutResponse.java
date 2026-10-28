package com.junaldadlawan.event_ticketing_api.layout.dto;

import com.junaldadlawan.event_ticketing_api.layout.entity.SeatLayout;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One organization layout, in full. */
public record SeatLayoutResponse(UUID id, UUID organizationId, String name, int version, int seatCount, double width, double height,
                                 List<VenueLayout.Section> sections, List<VenueLayout.Shape> shapes, Instant createdAt, Instant updatedAt)
        implements Serializable {

    public static SeatLayoutResponse of(SeatLayout entity, VenueLayout layout) {
        return new SeatLayoutResponse(entity.getId(), entity.getOrganizationId(), entity.getName(), entity.getVersion(), entity.getSeatCount(),
                entity.getWidth(), entity.getHeight(), layout.sections(), layout.shapes(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
