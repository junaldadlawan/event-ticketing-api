package com.junaldadlawan.event_ticketing_api.order.controller;

import com.junaldadlawan.event_ticketing_api.order.dto.CheckoutStartResponse;
import com.junaldadlawan.event_ticketing_api.order.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Where the web app polls a checkout that waits for the payment provider until it is PAID (then {@code orderId} is set) or FAILED.
 * Authenticated by the catch-all rule in SecurityConfig; only the buyer who started the checkout can read it (others get a 404).
 */
@RestController
@RequestMapping("/api/v1/checkouts")
@RequiredArgsConstructor
public class CheckoutStatusController {

    private final CheckoutService checkoutService;

    @GetMapping("/{checkoutId}")
    public CheckoutStartResponse get(@PathVariable UUID checkoutId) {
        return checkoutService.getStatus(checkoutId);
    }
}
