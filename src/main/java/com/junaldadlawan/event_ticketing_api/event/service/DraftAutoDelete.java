package com.junaldadlawan.event_ticketing_api.event.service;

import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * BR-EVENT-007: when an unpublished draft is deleted. The cutoff (minutes before the start) is the platform setting; this keeps its latest value
 * so {@code EventResponse} can show {@code autoDeleteAt} without a lookup. It is refreshed whenever the setting is read or changed and by every
 * cleanup run, so another instance sees a change within a minute.
 */
public final class DraftAutoDelete {

    private static volatile int minutesBeforeStart = 60;

    private DraftAutoDelete() {
    }

    public static void setMinutesBeforeStart(int minutes) {
        minutesBeforeStart = minutes;
    }

    public static int minutesBeforeStart() {
        return minutesBeforeStart;
    }

    /** The moment a draft is deleted (its start minus the cutoff); null for an event that is not a draft, or when the rule is off. */
    public static Instant autoDeleteAt(Event event) {
        int minutes = minutesBeforeStart;
        if (event.getStatus() != EventStatus.DRAFT || minutes <= 0 || event.getStartAt() == null) {
            return null;
        }
        return event.getStartAt().minus(Duration.ofMinutes(minutes));
    }
}
