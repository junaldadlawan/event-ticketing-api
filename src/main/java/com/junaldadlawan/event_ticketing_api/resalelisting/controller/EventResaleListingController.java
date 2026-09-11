package com.junaldadlawan.event_ticketing_api.resalelisting.controller;

import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResaleListingResponse;
import com.junaldadlawan.event_ticketing_api.resalelisting.service.ResaleListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code GET /events/{eventId}/resale-listings} is public (openapi.yaml's
 * {@code security: []}), already covered by SecurityConfig's existing broad
 * {@code GET /api/v1/events/**} permitAll matcher - no SecurityConfig
 * changes needed.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/resale-listings")
@RequiredArgsConstructor
public class EventResaleListingController {

    private final ResaleListingService resaleListingService;

    @GetMapping
    public PageResponse<ResaleListingResponse> listActive(@PathVariable UUID eventId, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(resaleListingService.listActive(eventId, pageable));
    }
}
