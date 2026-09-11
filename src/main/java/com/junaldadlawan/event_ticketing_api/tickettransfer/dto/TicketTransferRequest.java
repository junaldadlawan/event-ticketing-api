package com.junaldadlawan.event_ticketing_api.tickettransfer.dto;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.UUID;

/** Matches openapi.yaml's {@code POST /tickets/{ticketId}/transfer} request body. */
public record TicketTransferRequest(
        @NotNull
        UUID toUserId) implements Serializable {
}
