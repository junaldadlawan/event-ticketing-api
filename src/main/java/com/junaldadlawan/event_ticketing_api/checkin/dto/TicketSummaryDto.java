package com.junaldadlawan.event_ticketing_api.checkin.dto;

import java.io.Serializable;

/** Nested object within {@code ValidationResult}, matching openapi.yaml's {@code ticket_summary}. */
public record TicketSummaryDto(
        String ticketNumber,
        String ticketTypeName,
        String seat) implements Serializable {
}
