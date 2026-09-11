package com.junaldadlawan.event_ticketing_api.resalepolicy.dto;

import com.junaldadlawan.event_ticketing_api.resalepolicy.entity.ResalePolicy;
import com.junaldadlawan.event_ticketing_api.resalepolicy.enums.PriceCapRule;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link ResalePolicy}, matching openapi.yaml's {@code ResalePolicy}
 * schema ({@code enabled} defaults to {@code false} there). No {@code
 * ResalePolicy} row is created for an event until an organizer first PATCHes
 * one - {@link #defaultFor} synthesizes the documented default response
 * ({@code enabled=false}, everything else null) so {@code GET
 * /events/{eventId}/resale-policy} never 404s on an unconfigured event.
 */
public record ResalePolicyResponse(
        UUID eventId,
        boolean enabled,
        PriceCapRule priceCapRule,
        MoneyDto feeAmount,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) implements Serializable {

    public static ResalePolicyResponse from(ResalePolicy policy) {
        return new ResalePolicyResponse(
                policy.getEventId(),
                policy.isEnabled(),
                policy.getPriceCapRule(),
                policy.getFeeAmount() != null ? new MoneyDto(policy.getFeeAmount().getAmount(), policy.getFeeAmount().getCurrency()) : null,
                policy.getCreatedAt(),
                policy.getCreatedBy(),
                policy.getUpdatedAt(),
                policy.getUpdatedBy());
    }

    public static ResalePolicyResponse defaultFor(UUID eventId) {
        return new ResalePolicyResponse(eventId, false, null, null, null, null, null, null);
    }
}
