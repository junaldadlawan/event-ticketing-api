package com.junaldadlawan.event_ticketing_api.dispute.dto;

import com.junaldadlawan.event_ticketing_api.dispute.entity.Dispute;
import com.junaldadlawan.event_ticketing_api.dispute.enums.DisputeStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** DTO for {@link Dispute}, matching openapi.yaml's {@code Dispute} schema. */
public record DisputeResponse(
        UUID id,
        UUID orderId,
        UUID ticketId,
        UUID raisedBy,
        DisputeStatus status,
        String reason,
        String resolution,
        Instant createdAt,
        Instant updatedAt,
        UUID updatedBy) implements Serializable {

    public static DisputeResponse from(Dispute dispute) {
        return new DisputeResponse(
                dispute.getId(),
                dispute.getOrderId(),
                dispute.getTicketId(),
                dispute.getRaisedBy(),
                dispute.getStatus(),
                dispute.getReason(),
                dispute.getResolution(),
                dispute.getCreatedAt(),
                dispute.getUpdatedAt(),
                dispute.getUpdatedBy());
    }
}
