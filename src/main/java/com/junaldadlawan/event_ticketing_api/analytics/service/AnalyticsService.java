package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.dto.EventAnalyticsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.PlatformAnalyticsResponse;

import java.util.UUID;

public interface AnalyticsService {

    /** {@code GET /events/{eventId}/analytics} (BR-ANALYTICS-001) - owning organizer/owner, or admin. */
    EventAnalyticsResponse getEventAnalytics(UUID eventId);

    /** {@code GET /analytics/platform} (BR-ANALYTICS-002) - admin only. */
    PlatformAnalyticsResponse getPlatformAnalytics();
}
