package com.junaldadlawan.event_ticketing_api.analytics.dto;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An event's funnel for the days {@code from}..{@code to} (both included, in the event's time zone), from the view and cart logs
 * (BR-ANALYTICS-003): page views, then tickets put in carts, then tickets checked out. {@code daily} has every day of the range (0 when none);
 * {@code ticketTypes} has every ticket type of the event in its arranged order. The rates are null when there is nothing to divide by.
 * {@code trackingSince} is when the logs started, so nothing before it was recorded.
 */
public record EventFunnelResponse(
        UUID eventId,
        String timezone,
        LocalDate from,
        LocalDate to,
        long views,
        long uniqueVisitors,
        AddToCart addToCart,
        CheckedOut checkedOut,
        Double viewToCartRate,
        Double viewToPurchaseRate,
        List<Day> daily,
        List<TicketTypeFunnel> ticketTypes,
        Instant trackingSince) implements Serializable {

    public record AddToCart(long actions, long quantity, long uniqueBuyers) implements Serializable {
    }

    public record CheckedOut(long quantity, long uniqueBuyers) implements Serializable {
    }

    public record Day(LocalDate day, long views, long uniqueVisitors, long addToCartQuantity, long checkedOutQuantity) implements Serializable {
    }

    public record TicketTypeFunnel(UUID ticketTypeId, String name, long addToCartActions, long addedQuantity, long uniqueBuyersAdding,
                                   long checkedOutQuantity, long removedQuantity, long expiredQuantity, Double cartConversionRate)
            implements Serializable {
    }
}
