package com.junaldadlawan.event_ticketing_api.refundpolicy.dto;

import com.junaldadlawan.event_ticketing_api.refundpolicy.enums.RefundRuleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Matches openapi.yaml's {@code RefundPolicyUpdate} schema: {@code rule_type}
 * required, {@code days_before_event}/{@code custom_terms} optional (only
 * meaningful for their respective rule types).
 */
public record RefundPolicyUpdateRequest(
        @NotNull
        RefundRuleType ruleType,

        Integer daysBeforeEvent,

        @Size(max = 2_000)
        String customTerms) implements Serializable {
}
