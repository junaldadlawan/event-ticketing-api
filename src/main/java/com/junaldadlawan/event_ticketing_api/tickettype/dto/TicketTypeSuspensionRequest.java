package com.junaldadlawan.event_ticketing_api.tickettype.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** Body of {@code POST /api/v1/ticket-types/{id}/suspension} (admin): why the ticket type is suspended, shown to the organizer. */
public record TicketTypeSuspensionRequest(
        @NotBlank
        @Size(max = 1000)
        @NoHtml
        String reason) implements Serializable {
}
