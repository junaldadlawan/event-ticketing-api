package com.junaldadlawan.event_ticketing_api.tickettype.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.enums.SalesStatus;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * Body of {@code PUT /api/v1/ticket-types/{id}/sales-status}: the state the organizer wants the ticket type
 * to be in. Asking for the state it is already in is fine and changes nothing.
 */
public record TicketTypeSalesStatusRequest(
        @NotNull
        SalesStatus status) implements Serializable {
}
