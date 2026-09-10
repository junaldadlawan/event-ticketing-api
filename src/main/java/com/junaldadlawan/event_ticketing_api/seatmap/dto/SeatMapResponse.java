package com.junaldadlawan.event_ticketing_api.seatmap.dto;

import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;

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
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) implements Serializable {

    public static SeatMapResponse from(SeatMap seatMap, List<SeatResponse> seats) {
        return new SeatMapResponse(
                seatMap.getId(),
                seatMap.getEventId(),
                seats,
                seatMap.getCreatedBy(),
                seatMap.getCreatedAt(),
                seatMap.getUpdatedBy(),
                seatMap.getUpdatedAt());
    }
}
