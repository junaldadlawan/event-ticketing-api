package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.dto.EventAnalyticsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.EventAudienceResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.EventFunnelResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.EventSalesPatternsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.PlatformAnalyticsResponse;

import java.util.UUID;

public interface AnalyticsService {

    /** {@code GET /events/{eventId}/analytics} (BR-ANALYTICS-001) - owning organizer/owner, or admin. */
    EventAnalyticsResponse getEventAnalytics(UUID eventId);

    /** {@code GET /events/{eventId}/analytics/audience} - same access. Buyer age groups, aggregates only; groups of 1-4 buyers come back null. */
    EventAudienceResponse getEventAudience(UUID eventId);

    /** {@code GET /events/{eventId}/analytics/sales-patterns} - same access. Tickets per day, weekday and hour in the event's time zone. */
    EventSalesPatternsResponse getEventSalesPatterns(UUID eventId);

    /**
     * {@code GET /events/{eventId}/analytics/funnel} - same access. Views, add-to-cart and checkout for the days {@code from}..{@code to} in the
     * event's time zone (default: since the event was created, up to today; at most 366 days). 400 for a bad range.
     */
    EventFunnelResponse getEventFunnel(UUID eventId, java.time.LocalDate from, java.time.LocalDate to);

    /** {@code GET /analytics/platform} (BR-ANALYTICS-002) - admin only. */
    PlatformAnalyticsResponse getPlatformAnalytics();
}
