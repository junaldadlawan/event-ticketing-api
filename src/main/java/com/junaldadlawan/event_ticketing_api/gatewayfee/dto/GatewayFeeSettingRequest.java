package com.junaldadlawan.event_ticketing_api.gatewayfee.dto;

import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Body of {@code PUT /api/v1/payment-gateway-fee}: a gateway's fee has two numbers, a percentage and a fixed amount, for example
 * PayMongo's card rate "3.125% + PHP 13.39" is {@code percentage} 3.125 and {@code flatAmount} {@code {amount: 1339, currency: "PHP"}}
 * (minor units: 1339 centavos = PHP 13.39). {@code percentage} is 0-100 with up to three decimals; the fixed amount is optional and means
 * none when left out; {@code payer} says who bears the fee. 0% with no fixed amount switches the fee off. That is the **domestic** rate; a card
 * issued abroad costs more with PayMongo (3.5% + PHP 15.00 domestic, 4.5% + PHP 15.00 foreign), so {@code foreignPercentage} and
 * {@code foreignFlatAmount} set the whole rate for such cards (same currency as the domestic fixed amount). Leave both out and a foreign card
 * is charged the domestic rate. The customer pays the domestic rate in the cart; the foreign rate applies when the payment shows the card is foreign.
 */
public record GatewayFeeSettingRequest(
        @NotNull
        @DecimalMin("0")
        @DecimalMax("100")
        @Digits(integer = 3, fraction = 3)
        BigDecimal percentage,

        @Valid
        MoneyDto flatAmount,

        @NotNull
        GatewayFeePayer payer,

        @DecimalMin("0")
        @DecimalMax("100")
        @Digits(integer = 3, fraction = 3)
        BigDecimal foreignPercentage,

        @Valid
        MoneyDto foreignFlatAmount) implements Serializable {

    /** The domestic rate only: cards issued abroad cost the same. */
    public GatewayFeeSettingRequest(BigDecimal percentage, MoneyDto flatAmount, GatewayFeePayer payer) {
        this(percentage, flatAmount, payer, null, null);
    }
}
