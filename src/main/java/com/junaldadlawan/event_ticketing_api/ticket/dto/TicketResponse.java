package com.junaldadlawan.event_ticketing_api.ticket.dto;

import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link Ticket}, matching openapi.yaml's {@code Ticket} schema
 * exactly. Deliberately has NO {@code credential} field — the raw scannable
 * credential is never returned via any API read, per openapi's own summary
 * text on {@code GET /tickets/{ticketId}}.
 */
public record TicketResponse(
        UUID id,
        UUID orderId,
        UUID eventId,
        UUID ticketTypeId,
        UUID seatId,
        UUID ownerId,
        String ticketNumber,
        TicketStatus status,
        Instant createdAt,
        Instant updatedAt,
        // only on a USED ticket: the valid scan that checked it in
        TicketCheckInResponse checkIn) implements Serializable {

    public static TicketResponse from(Ticket ticket) {
        return from(ticket, null);
    }

    public static TicketResponse from(Ticket ticket, TicketCheckInResponse checkIn) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getOrderId(),
                ticket.getEventId(),
                ticket.getTicketTypeId(),
                ticket.getSeatId(),
                ticket.getOwnerId(),
                ticket.getTicketNumber(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                checkIn);
    }
}
