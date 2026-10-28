package com.junaldadlawan.event_ticketing_api.tickettype.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link TicketType}
 */
public record TicketTypeResponse(
        UUID id,
        UUID eventId,
        String name,
        TicketTypeKind kind,
        MoneyDto price,
        int quantityTotal,
        int quantityAvailable,
        Instant saleStartAt,
        Instant saleEndAt,
        int maxPerOrder,
        boolean salesPaused,
        /** An admin suspended it: buyers see it but cannot buy it, and only an admin can lift it. */
        boolean suspended,
        /** Why it was suspended; only for the event's organizers and admins (null for anyone else, and when not suspended). */
        String suspensionReason,
        Instant suspendedAt,
        String color,
        Integer maxPerUser,
        int position,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) implements Serializable {

    public static TicketTypeResponse from(TicketType ticketType) {
        return new TicketTypeResponse(
                ticketType.getId(),
                ticketType.getEventId(),
                ticketType.getName(),
                ticketType.getKind(),
                new MoneyDto(ticketType.getPrice().getAmount(), ticketType.getPrice().getCurrency()),
                ticketType.getQuantityTotal(),
                ticketType.getQuantityAvailable(),
                ticketType.getSaleStartAt(),
                ticketType.getSaleEndAt(),
                ticketType.getMaxPerOrder(),
                ticketType.isSalesPaused(),
                ticketType.isSuspended(),
                ticketType.getSuspensionReason(),
                ticketType.getSuspendedAt(),
                ticketType.getColor(),
                ticketType.getMaxPerUser(),
                ticketType.getPosition(),
                ticketType.getCreatedBy(),
                ticketType.getCreatedAt(),
                ticketType.getUpdatedBy(),
                ticketType.getUpdatedAt());
    }

    /** The same without the suspension reason, for callers who do not manage the event. */
    public TicketTypeResponse withoutReason() {
        return new TicketTypeResponse(id, eventId, name, kind, price, quantityTotal, quantityAvailable, saleStartAt, saleEndAt, maxPerOrder,
                salesPaused, suspended, null, suspendedAt, color, maxPerUser, position, createdBy, createdAt, updatedBy, updatedAt);
    }
}
