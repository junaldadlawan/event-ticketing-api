package com.junaldadlawan.event_ticketing_api.payout.controller;

import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutGenerateRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutResponse;
import com.junaldadlawan.event_ticketing_api.payout.service.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Falls under the existing {@code /api/v1/organizations/**} authenticated
 * matcher - no SecurityConfig changes needed. Reading is for the organization's owner/organizer or an admin;
 * generating a payout (which deducts the platform fees) is admin only.
 */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}/payouts")
@RequiredArgsConstructor
public class OrganizationPayoutController {

    private final PayoutService payoutService;

    @GetMapping
    public PageResponse<PayoutResponse> list(@PathVariable UUID orgId, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(payoutService.list(orgId, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PayoutResponse generate(@PathVariable UUID orgId, @Valid @RequestBody PayoutGenerateRequest request) {
        return payoutService.generate(orgId, request);
    }
}
