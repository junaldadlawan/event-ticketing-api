package com.junaldadlawan.event_ticketing_api.gatewayfee.service;

import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeCheckResponse;

public interface GatewayFeeCheckService {

    /**
     * Admin only. Compares the fee setting with what the gateway really charged on its {@code limit} (1 to 20) most recent paid payments.
     * 400 for a limit outside that range; 502 when the gateway cannot be read.
     */
    GatewayFeeCheckResponse check(int limit);
}
