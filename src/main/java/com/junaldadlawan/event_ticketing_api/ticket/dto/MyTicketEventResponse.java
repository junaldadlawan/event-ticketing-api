package com.junaldadlawan.event_ticketing_api.ticket.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** {@code GET /users/me/tickets/events}: an event the caller holds tickets for, and how many (any status). */
public record MyTicketEventResponse(
        UUID id,
        String title,
        Instant startAt,
        String timezone,
        long ticketCount) implements Serializable {
}
