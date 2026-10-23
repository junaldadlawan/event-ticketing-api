package com.junaldadlawan.event_ticketing_api.checkin.history;

import java.io.Serializable;

/**
 * Counts of an event's scan attempts for the same filters as the history. {@code online} are scans by a scanner device, {@code offline} fallback
 * scans reconciled later. {@code uniqueTicketsAdmitted} counts tickets with at least one VALID scan; {@code ticketsAdmittable} is every ticket of the
 * event that is not refunded or cancelled (not affected by the filters).
 */
public record CheckInSummaryResponse(
        long total,
        long valid,
        long duplicate,
        long invalid,
        long wrongEvent,
        long online,
        long offline,
        long uniqueTicketsAdmitted,
        long ticketsAdmittable) implements Serializable {
}
