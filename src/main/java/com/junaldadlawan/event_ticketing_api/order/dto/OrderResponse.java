package com.junaldadlawan.event_ticketing_api.order.dto;

import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for {@link Order}, matching openapi.yaml's {@code Order} schema.
 * {@code tickets} is always an empty list: ticket issuance (BR-TICKET-001/
 * 002, the {@code Ticket} entity itself) is explicitly out of scope for this
 * dispatch and deferred to Phase 6 per the roadmap - kept here only so the
 * response shape matches openapi's documented {@code Order.tickets} field
 * rather than omitting it outright.
 */
public record OrderResponse(
        UUID id,
        UUID buyerId,
        PayeeType payeeType,
        UUID payeeId,
        OrderStatus status,
        String promoCode,
        MoneyDto total,
        List<Object> tickets,
        Instant createdAt,
        String createdBy,
        Instant updatedAt) implements Serializable {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getBuyerId(),
                order.getPayeeType(),
                order.getPayeeId(),
                order.getStatus(),
                order.getPromoCode(),
                new MoneyDto(order.getTotal().getAmount(), order.getTotal().getCurrency()),
                List.of(),
                order.getCreatedAt(),
                order.getCreatedBy(),
                order.getUpdatedAt());
    }
}
