package com.junaldadlawan.event_ticketing_api.analytics.controller;

import com.junaldadlawan.event_ticketing_api.analytics.dto.EventAnalyticsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.EventAudienceResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.EventSalesPatternsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.ShareTimelineResponse;
import com.junaldadlawan.event_ticketing_api.analytics.service.AnalyticsService;
import com.junaldadlawan.event_ticketing_api.analytics.dto.EventFunnelResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
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

    @GetMapping("/audience")
    public EventAudienceResponse audience(@PathVariable UUID eventId) {
        return analyticsService.getEventAudience(eventId);
    }

    @GetMapping("/funnel")
    public EventFunnelResponse funnel(@PathVariable UUID eventId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.getEventFunnel(eventId, from, to);
    }

    @GetMapping("/share-timeline")
    public ShareTimelineResponse shareTimeline(@PathVariable UUID eventId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.getEventShareTimeline(eventId, from, to);
    }

    @GetMapping("/sales-patterns")
    public EventSalesPatternsResponse salesPatterns(@PathVariable UUID eventId) {
        return analyticsService.getEventSalesPatterns(eventId);
    }
}
