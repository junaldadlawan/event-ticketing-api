package com.junaldadlawan.event_ticketing_api.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.UUID;

/**
 * Matches openapi.yaml's {@code DisputeCreate} schema. At least one of
 * {@code orderId}/{@code ticketId} must be provided - enforced in {@code
 * DisputeServiceImpl.create}, not expressible as a single-field bean
 * validation annotation.
 */
public record DisputeCreateRequest(
        UUID orderId,

        UUID ticketId,

        @NotBlank
        @Size(max = 1000)
        String reason) implements Serializable {
}
