package com.junaldadlawan.event_ticketing_api.seatmap.dto;

import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;

import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link Seat}. Read-only — no create/update endpoint exists for
 * seats in this phase's settled contract.
 */
public record SeatResponse(
        UUID id,
        String section,
        String row,
        String seatNumber,
        SeatStatus status,
        UUID sectionId,
        UUID ticketTypeId,
        Double x,
        Double y,
        VenueLayout.SeatType type,
        String sectionColor,
        VenueLayout.SectionKind sectionKind,
        Instant createdAt,
        Instant updatedAt) implements Serializable {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getSection(),
                seat.getRow(),
                seat.getSeatNumber(),
                seat.getStatus(),
                seat.getLayoutSectionId(),
                seat.getTicketTypeId(),
                seat.getX(),
                seat.getY(),
                seat.getSeatType(),
                seat.getSectionColor(),
                seat.getSectionKind(),
                seat.getCreatedAt(),
                seat.getUpdatedAt());
    }
}
