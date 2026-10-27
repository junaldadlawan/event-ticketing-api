package com.junaldadlawan.event_ticketing_api.seatmap.dto;

import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;

import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for {@link SeatMap}, embedding its {@link SeatResponse} list per
 * openapi.yaml's nested {@code SeatMap.seats} shape.
 */
public record SeatMapResponse(
        UUID id,
        UUID eventId,
        List<SeatResponse> seats,
        Double width,
        Double height,
        List<VenueLayout.Shape> shapes,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) implements Serializable {

    /** {@code width}, {@code height} and {@code shapes} are the plan the seats were copied with (null when they were not copied from a layout). */
    public static SeatMapResponse from(SeatMap seatMap, List<SeatResponse> seats, List<VenueLayout.Shape> shapes) {
        return new SeatMapResponse(
                seatMap.getId(),
                seatMap.getEventId(),
                seats,
                seatMap.getLayoutWidth(),
                seatMap.getLayoutHeight(),
                shapes,
                seatMap.getCreatedBy(),
                seatMap.getCreatedAt(),
                seatMap.getUpdatedBy(),
                seatMap.getUpdatedAt());
    }
}
