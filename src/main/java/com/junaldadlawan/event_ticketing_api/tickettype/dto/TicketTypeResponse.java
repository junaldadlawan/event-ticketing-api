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
                ticketType.getCreatedBy(),
                ticketType.getCreatedAt(),
                ticketType.getUpdatedBy(),
                ticketType.getUpdatedAt());
    }
}
