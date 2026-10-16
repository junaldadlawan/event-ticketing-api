package com.junaldadlawan.event_ticketing_api.gatewayfee.service;

import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;

import java.math.BigDecimal;

/**
 * The payment gateway fee for one purchase, with a snapshot of the setting that produced it (stored on the order). {@code amount} is the
 * domestic fee, what the customer is charged in the cart; {@code foreignAmount} is what the same purchase would cost with a card issued
 * abroad (the same as {@code amount} when no foreign rate is set). The setting fields are null when no fee applies.
 */
public record GatewayFeeQuote(
        long amount,
        GatewayFeePayer payer,
        BigDecimal percentage,
        Long flatAmount,
        long foreignAmount,
        BigDecimal foreignPercentage,
        Long foreignFlatAmount) {

    /** A fee whose rate is the same for a card issued abroad. */
    public GatewayFeeQuote(long amount, GatewayFeePayer payer, BigDecimal percentage, Long flatAmount) {
        this(amount, payer, percentage, flatAmount, amount, percentage, flatAmount);
    }

    /** No fee applies. */
    public static final GatewayFeeQuote NONE = new GatewayFeeQuote(0, null, null, null, 0, null, null);

    /** The part the buyer pays on top of the price: the whole fee when the customer bears it, otherwise nothing. */
    public long chargedToBuyer() {
        return payer == GatewayFeePayer.CUSTOMER ? amount : 0;
    }
}
