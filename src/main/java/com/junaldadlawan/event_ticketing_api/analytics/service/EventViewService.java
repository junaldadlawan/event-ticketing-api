package com.junaldadlawan.event_ticketing_api.analytics.service;

import java.util.UUID;

public interface EventViewService {

    /**
     * {@code POST /events/{eventId}/views} - public. Records one view of a public event's page, at most one per event and visitor per 30
     * minutes; the event's own owner/organizers and admins, events that are not public and rate-limited clients are not counted.
     * {@code clientKey} identifies the caller for the rate limit only and is never stored. Returns whether a view row was written.
     */
    boolean recordView(UUID eventId, String visitorId, String clientKey);
}
