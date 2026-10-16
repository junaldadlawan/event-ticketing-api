package com.junaldadlawan.event_ticketing_api.platformfee.dto;

import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeType;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Body of the {@code PUT /api/v1/platform-fees/...} endpoints. Send {@code percentage} (0-100, at most two decimals),
 * {@code flatAmount} (minor units + currency, charged for EACH ticket), or both (the fee is then the percentage plus the flat amount per ticket); at least one is
 * required. A rate of 0 is allowed: it waives the fee for that scope. {@code payer} says who bears the fee and defaults to
 * CUSTOMER; only CUSTOMER and ORGANIZATION are accepted (PLATFORM is refused with 400). {@code type} is ignored (the kind of fee follows from which numbers are sent); it stays only so older callers still work.
 */
public record PlatformFeeRuleRequest(
        FeeType type,

        @DecimalMin("0")
        @DecimalMax("100")
        @Digits(integer = 3, fraction = 2)
        BigDecimal percentage,

        @Valid
        MoneyDto flatAmount,

        GatewayFeePayer payer) implements Serializable {
}
