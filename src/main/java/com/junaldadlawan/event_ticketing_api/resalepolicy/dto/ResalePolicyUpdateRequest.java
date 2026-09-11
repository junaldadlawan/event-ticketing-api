package com.junaldadlawan.event_ticketing_api.resalepolicy.dto;

import com.junaldadlawan.event_ticketing_api.resalepolicy.enums.PriceCapRule;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * Matches openapi.yaml's {@code ResalePolicyUpdate} schema: {@code enabled}
 * required, {@code priceCapRule}/{@code feeAmount} optional (only meaningful
 * when {@code priceCapRule == FACE_VALUE_PLUS_FEE}).
 */
public record ResalePolicyUpdateRequest(
        @NotNull
        Boolean enabled,

        PriceCapRule priceCapRule,

        @Valid
        MoneyDto feeAmount) implements Serializable {
}
