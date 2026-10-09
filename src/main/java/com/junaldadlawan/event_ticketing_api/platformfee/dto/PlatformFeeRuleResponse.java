package com.junaldadlawan.event_ticketing_api.platformfee.dto;

import com.junaldadlawan.event_ticketing_api.platformfee.entity.PlatformFeeRule;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeScope;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeType;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** DTO for {@link PlatformFeeRule}. {@code scopeId} is null for the platform default. */
public record PlatformFeeRuleResponse(
        UUID id,
        FeeScope scope,
        UUID scopeId,
        FeeType type,
        BigDecimal percentage,
        MoneyDto flatAmount,
        Instant createdAt,
        Instant updatedAt) implements Serializable {

    public static PlatformFeeRuleResponse from(PlatformFeeRule rule) {
        return new PlatformFeeRuleResponse(rule.getId(), rule.getScope(), rule.getScopeId(), rule.getType(),
                rule.getPercentage(),
                rule.getFlatAmount() == null ? null : new MoneyDto(rule.getFlatAmount(), rule.getFlatCurrency()),
                rule.getCreatedAt(), rule.getUpdatedAt());
    }
}
