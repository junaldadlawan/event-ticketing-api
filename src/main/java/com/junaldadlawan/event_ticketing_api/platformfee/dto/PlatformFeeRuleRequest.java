package com.junaldadlawan.event_ticketing_api.platformfee.dto;

import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeType;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Body of the {@code PUT /api/v1/platform-fees/...} endpoints. PERCENTAGE needs {@code percentage} (0-100, at most two
 * decimals) and no {@code flatAmount}; FLAT needs {@code flatAmount} (minor units + currency) and no
 * {@code percentage}. A rate of 0 is allowed: it waives the fee for that scope.
 */
public record PlatformFeeRuleRequest(
        @NotNull
        FeeType type,

        @DecimalMin("0")
        @DecimalMax("100")
        @Digits(integer = 3, fraction = 2)
        BigDecimal percentage,

        @Valid
        MoneyDto flatAmount) implements Serializable {
}
