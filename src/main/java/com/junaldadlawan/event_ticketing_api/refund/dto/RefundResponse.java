package com.junaldadlawan.event_ticketing_api.refund.dto;

import com.junaldadlawan.event_ticketing_api.refund.entity.Refund;
import com.junaldadlawan.event_ticketing_api.refund.enums.RefundStatus;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** DTO for {@link Refund}, matching openapi.yaml's {@code Refund} schema. */
public record RefundResponse(
        UUID id,
        UUID orderId,
        MoneyDto amount,
        String reason,
        UUID initiatedBy,
        RefundStatus status,
        Instant createdAt,
        Instant updatedAt) implements Serializable {

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getOrderId(),
                new MoneyDto(refund.getAmount().getAmount(), refund.getAmount().getCurrency()),
                refund.getReason(),
                refund.getInitiatedBy(),
                refund.getStatus(),
                refund.getCreatedAt(),
                refund.getUpdatedAt());
    }
}
