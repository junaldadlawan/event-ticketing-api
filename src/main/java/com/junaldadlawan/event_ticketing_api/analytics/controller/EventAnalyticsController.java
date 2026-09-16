package com.junaldadlawan.event_ticketing_api.analytics.controller;

import com.junaldadlawan.event_ticketing_api.analytics.dto.EventAnalyticsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** {@code GET /api/v1/events/{eventId}/analytics} - owning organizer/owner, or admin (BR-ANALYTICS-001). */
@RestController
@RequestMapping("/api/v1/events/{eventId}/analytics")
@RequiredArgsConstructor
public class EventAnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public EventAnalyticsResponse get(@PathVariable UUID eventId) {
        return analyticsService.getEventAnalytics(eventId);
    }
}
