package com.junaldadlawan.event_ticketing_api.checkin.dto;

import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Matches openapi.yaml's {@code ValidationResult} schema. */
public record ValidationResultResponse(
        UUID ticketId,
        CheckInResult result,
        Instant scannedAt,
        TicketSummaryDto ticketSummary) implements Serializable {
}
