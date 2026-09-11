package com.junaldadlawan.event_ticketing_api.refundpolicy.dto;

import com.junaldadlawan.event_ticketing_api.refundpolicy.entity.RefundPolicy;
import com.junaldadlawan.event_ticketing_api.refundpolicy.enums.RefundRuleType;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link RefundPolicy}, matching openapi.yaml's {@code RefundPolicy}
 * schema. No {@code RefundPolicy} row is created for an event until an
 * organizer first PATCHes one - {@link #defaultFor} synthesizes a {@code
 * NO_REFUNDS} default response (RefundServiceImpl's confirmed decision:
 * an unconfigured event permits no refunds, the conservative default,
 * mirroring ResalePolicy's own default-disabled precedent) so {@code GET
 * /events/{eventId}/refund-policy} never 404s on an unconfigured event.
 */
public record RefundPolicyResponse(
        UUID eventId,
        RefundRuleType ruleType,
        Integer daysBeforeEvent,
        String customTerms,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) implements Serializable {

    public static RefundPolicyResponse from(RefundPolicy policy) {
        return new RefundPolicyResponse(
                policy.getEventId(),
                policy.getRuleType(),
                policy.getDaysBeforeEvent(),
                policy.getCustomTerms(),
                policy.getCreatedAt(),
                policy.getCreatedBy(),
                policy.getUpdatedAt(),
                policy.getUpdatedBy());
    }

    public static RefundPolicyResponse defaultFor(UUID eventId) {
        return new RefundPolicyResponse(eventId, RefundRuleType.NO_REFUNDS, null, null, null, null, null, null);
    }
}
