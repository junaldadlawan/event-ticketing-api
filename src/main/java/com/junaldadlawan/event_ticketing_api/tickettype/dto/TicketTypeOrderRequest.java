package com.junaldadlawan.event_ticketing_api.tickettype.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code PUT /api/v1/events/{eventId}/ticket-types/order}: the event's ticket types in the order the
 * organizer arranged them (first = position 0). It must list every ticket type of the event exactly once;
 * the service rejects a list that repeats, omits or invents one, so a stale screen can never silently drop
 * a ticket type from the arrangement.
 */
public record TicketTypeOrderRequest(
        @NotEmpty
        List<@NotNull UUID> ticketTypeIds) implements Serializable {
}
