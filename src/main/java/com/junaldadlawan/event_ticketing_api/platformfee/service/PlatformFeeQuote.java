package com.junaldadlawan.event_ticketing_api.platformfee.service;

import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeScope;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeType;

import java.math.BigDecimal;

/**
 * The platform fee for one purchase, with a snapshot of the rule that produced it (stored on the order). The rule
 * fields are null when no rule applies.
 */
public record PlatformFeeQuote(
        long amount,
        FeeScope scope,
        FeeType type,
        BigDecimal percentage,
        Long flatAmount,
        GatewayFeePayer payer) {

    /** No rule applies: no fee. */
    public static final PlatformFeeQuote NONE = new PlatformFeeQuote(0, null, null, null, null, null);

    /** The part the buyer pays on top of the price: the whole fee when the customer bears it, otherwise nothing. */
    public long chargedToBuyer() {
        return payer == GatewayFeePayer.CUSTOMER ? amount : 0;
    }
}
