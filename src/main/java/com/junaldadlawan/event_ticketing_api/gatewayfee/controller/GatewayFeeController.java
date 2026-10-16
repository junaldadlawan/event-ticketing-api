package com.junaldadlawan.event_ticketing_api.gatewayfee.controller;

import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeSettingRequest;
import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeSettingResponse;
import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeCheckResponse;
import com.junaldadlawan.event_ticketing_api.gatewayfee.service.GatewayFeeCheckService;
import com.junaldadlawan.event_ticketing_api.gatewayfee.service.GatewayFeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The payment gateway fee setting: how much to apply and who pays it (customer, organization or platform). Admin only, checked in the
 * service; the path falls under SecurityConfig's authenticated catch-all. Buyers never call this: they see the fee in their cart.
 */
@RestController
@RequestMapping("/api/v1/payment-gateway-fee")
@RequiredArgsConstructor
public class GatewayFeeController {

    private final GatewayFeeService gatewayFeeService;
    private final GatewayFeeCheckService gatewayFeeCheckService;

    @GetMapping
    public GatewayFeeSettingResponse get() {
        return gatewayFeeService.get();
    }

    /** The setting next to what the gateway really charged on its latest paid payments; {@code limit} is 1 to 20 (default 5). */
    @GetMapping("/check")
    public GatewayFeeCheckResponse check(@RequestParam(defaultValue = "5") int limit) {
        return gatewayFeeCheckService.check(limit);
    }

    @PutMapping
    public GatewayFeeSettingResponse update(@Valid @RequestBody GatewayFeeSettingRequest request) {
        return gatewayFeeService.update(request);
    }
}
