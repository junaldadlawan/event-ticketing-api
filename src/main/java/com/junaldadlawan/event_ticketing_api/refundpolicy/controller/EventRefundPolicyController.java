package com.junaldadlawan.event_ticketing_api.refundpolicy.controller;

import com.junaldadlawan.event_ticketing_api.refundpolicy.dto.RefundPolicyResponse;
import com.junaldadlawan.event_ticketing_api.refundpolicy.dto.RefundPolicyUpdateRequest;
import com.junaldadlawan.event_ticketing_api.refundpolicy.service.RefundPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Unlike {@code EventResalePolicyController}'s GET, this one is NOT public
 * (openapi.yaml: "organizer, admin, or a buyer with an order on it") - needs
 * its own SecurityConfig matcher, declared before the broad {@code GET
 * /api/v1/events/**} permitAll matcher, same idiom as promo-codes/orders/
 * ticket-templates. PATCH is already covered by the existing {@code PATCH
 * /api/v1/events/**} authenticated matcher.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/refund-policy")
@RequiredArgsConstructor
public class EventRefundPolicyController {

    private final RefundPolicyService refundPolicyService;

    @GetMapping
    public RefundPolicyResponse get(@PathVariable UUID eventId) {
        return refundPolicyService.get(eventId);
    }

    @PatchMapping
    public RefundPolicyResponse update(@PathVariable UUID eventId, @Valid @RequestBody RefundPolicyUpdateRequest request) {
        return refundPolicyService.update(eventId, request);
    }
}
