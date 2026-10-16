package com.junaldadlawan.event_ticketing_api.gatewayfee.service;

import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeSettingRequest;
import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeSettingResponse;

public interface GatewayFeeService {

    /**
     * The gateway fee for a purchase whose amount before this fee (ticket total after promo plus the platform fee) is
     * {@code baseAmount}: {@code base x percentage}, rounded half up to the minor unit, plus the fixed amount when the order is in the
     * fixed amount's currency (for example 3.125% + PHP 13.39). No fee when the base is zero or nothing is configured.
     */
    GatewayFeeQuote quote(long baseAmount, String currency);

    /** The current setting. Admin only. */
    GatewayFeeSettingResponse get();

    /** Replaces the setting. Admin only. Orders already placed keep the fee they were charged. */
    GatewayFeeSettingResponse update(GatewayFeeSettingRequest request);
}
