package com.junaldadlawan.event_ticketing_api.analytics.controller;

import com.junaldadlawan.event_ticketing_api.analytics.dto.PlatformAnalyticsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/analytics/platform} - admin only (BR-ANALYTICS-002). */
@RestController
@RequestMapping("/api/v1/analytics/platform")
@RequiredArgsConstructor
public class PlatformAnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public PlatformAnalyticsResponse get() {
        return analyticsService.getPlatformAnalytics();
    }
}
