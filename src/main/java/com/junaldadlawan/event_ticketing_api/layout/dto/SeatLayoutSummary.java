package com.junaldadlawan.event_ticketing_api.layout.dto;

import com.junaldadlawan.event_ticketing_api.layout.entity.SeatLayout;

import java.io.Serializable;
import java.util.UUID;

/** A row of {@code GET /organizations/{orgId}/layouts}. */
public record SeatLayoutSummary(UUID id, String name, double width, double height, int seatCount, int version) implements Serializable {

    public static SeatLayoutSummary from(SeatLayout layout) {
        return new SeatLayoutSummary(layout.getId(), layout.getName(), layout.getWidth(), layout.getHeight(), layout.getSeatCount(), layout.getVersion());
    }
}
