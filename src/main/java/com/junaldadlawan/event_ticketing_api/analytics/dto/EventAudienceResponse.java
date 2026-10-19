package com.junaldadlawan.event_ticketing_api.analytics.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Who bought tickets for an event, as aggregates only: {@code buyers} is the distinct buyers with a PAID or PARTIALLY_REFUNDED order,
 * {@code unknownAge} those without a birth date. Each age group counts a buyer once (by their age at their first order) and their live tickets.
 * A group of 1 to 4 buyers has {@code buyers} and {@code tickets} null ("fewer than 5"), so no single buyer can be picked out.
 */
public record EventAudienceResponse(UUID eventId, long buyers, long unknownAge, List<AgeGroup> ageGroups) implements Serializable {

    public record AgeGroup(String label, Long buyers, Long tickets) implements Serializable {
    }
}
