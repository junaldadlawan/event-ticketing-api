package com.junaldadlawan.event_ticketing_api.ticket.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** {@code Ticket.checkIn}: the valid scan that checked a USED ticket in - when, where, and by which scanner. */
public record TicketCheckInResponse(
        Instant scannedAt,
        String scannerLocation,
        UUID scannerId,
        String scannerName) implements Serializable {
}
