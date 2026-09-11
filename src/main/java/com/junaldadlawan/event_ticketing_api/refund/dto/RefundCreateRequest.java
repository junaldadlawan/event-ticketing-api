package com.junaldadlawan.event_ticketing_api.refund.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * Matches openapi.yaml's {@code RefundCreate} schema. {@code amount} is
 * optional - omitting it means a full refund of the order's remaining
 * refundable balance.
 */
public record RefundCreateRequest(
        @Valid
        MoneyDto amount,

        @NotBlank
        String reason) implements Serializable {
}
