package com.junaldadlawan.event_ticketing_api.analytics.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;

/** Matches openapi.yaml's {@code PlatformAnalytics} schema (BR-ANALYTICS-002). Computed, not entity-backed. */
public record PlatformAnalyticsResponse(MoneyDto totalGmv, long activeOrganizers, long eventVolume) implements Serializable {
}
