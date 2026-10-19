package com.junaldadlawan.event_ticketing_api.analytics.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * When an event's tickets sell, from its PAID and PARTIALLY_REFUNDED orders and live tickets only, in the event's time zone: {@code daily} has
 * every day from the first sale to today or the event's end (whichever is first), 0 when none; {@code byWeekday} has 7 counts, Monday first;
 * {@code byHour} has 24. All counts are tickets except {@code orders} and {@code promoOrders}.
 */
public record EventSalesPatternsResponse(
        UUID eventId,
        String timezone,
        long tickets,
        long orders,
        long promoOrders,
        List<Day> daily,
        long[] byWeekday,
        long[] byHour) implements Serializable {

    public record Day(LocalDate day, long tickets) implements Serializable {
    }
}
