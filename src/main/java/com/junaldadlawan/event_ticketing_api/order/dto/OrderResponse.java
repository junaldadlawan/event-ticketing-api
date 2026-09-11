package com.junaldadlawan.event_ticketing_api.order.dto;

import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.ticket.dto.TicketResponse;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for {@link Order}, matching openapi.yaml's {@code Order} schema.
 * {@code tickets} is now populated with the order's actually-issued Tickets
 * (Phase 6a) — {@code from} requires the caller to supply them (resolved via
 * {@code TicketRepository.findByOrderId}) rather than defaulting to empty,
 * so no call site can silently forget to look them up.
 */
public record OrderResponse(
        UUID id,
        UUID buyerId,
        PayeeType payeeType,
        UUID payeeId,
        OrderStatus status,
        String promoCode,
        MoneyDto total,
        List<TicketResponse> tickets,
        Instant createdAt,
        String createdBy,
        Instant updatedAt) implements Serializable {

    public static OrderResponse from(Order order, List<Ticket> tickets) {
        return new OrderResponse(
                order.getId(),
                order.getBuyerId(),
                order.getPayeeType(),
                order.getPayeeId(),
                order.getStatus(),
                order.getPromoCode(),
                new MoneyDto(order.getTotal().getAmount(), order.getTotal().getCurrency()),
                tickets.stream().map(TicketResponse::from).toList(),
                order.getCreatedAt(),
                order.getCreatedBy(),
                order.getUpdatedAt());
    }
}
