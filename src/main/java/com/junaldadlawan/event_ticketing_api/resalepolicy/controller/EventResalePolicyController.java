package com.junaldadlawan.event_ticketing_api.resalepolicy.controller;

import com.junaldadlawan.event_ticketing_api.resalepolicy.dto.ResalePolicyResponse;
import com.junaldadlawan.event_ticketing_api.resalepolicy.dto.ResalePolicyUpdateRequest;
import com.junaldadlawan.event_ticketing_api.resalepolicy.service.ResalePolicyService;
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
 * {@code GET} is public (openapi.yaml's {@code security: []} - a buyer needs
 * to know the policy before listing), already covered by SecurityConfig's
 * existing broad {@code GET /api/v1/events/**} permitAll matcher; {@code
 * PATCH} is owning-organizer-only, already covered by the existing {@code
 * PATCH /api/v1/events/**} authenticated matcher - no SecurityConfig changes
 * needed for this controller.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/resale-policy")
@RequiredArgsConstructor
public class EventResalePolicyController {

    private final ResalePolicyService resalePolicyService;

    @GetMapping
    public ResalePolicyResponse get(@PathVariable UUID eventId) {
        return resalePolicyService.get(eventId);
    }

    @PatchMapping
    public ResalePolicyResponse update(@PathVariable UUID eventId, @Valid @RequestBody ResalePolicyUpdateRequest request) {
        return resalePolicyService.update(eventId, request);
    }
}
