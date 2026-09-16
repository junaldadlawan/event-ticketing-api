package com.junaldadlawan.event_ticketing_api.analytics.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

/**
 * Matches openapi.yaml's {@code EventAnalytics} schema (BR-ANALYTICS-001).
 * Computed, not entity-backed - no {@code from(Entity)} factory, built
 * directly by {@code AnalyticsServiceImpl}.
 */
public record EventAnalyticsResponse(
        UUID eventId,
        int ticketsSold,
        MoneyDto revenue,
        int remainingInventory,
        List<SalesOverTimeEntry> salesOverTime) implements Serializable {
}
