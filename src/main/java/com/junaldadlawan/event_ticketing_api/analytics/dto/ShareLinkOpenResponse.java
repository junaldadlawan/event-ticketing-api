package com.junaldadlawan.event_ticketing_api.analytics.dto;

import java.util.UUID;

/** {@code POST /share-links/{code}/open}: the event the opened link points at. */
public record ShareLinkOpenResponse(UUID eventId) {
}
