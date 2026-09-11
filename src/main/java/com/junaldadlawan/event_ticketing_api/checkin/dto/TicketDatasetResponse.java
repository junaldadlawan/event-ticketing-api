package com.junaldadlawan.event_ticketing_api.checkin.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Matches openapi.yaml's {@code TicketDataset} schema. */
public record TicketDatasetResponse(
        UUID eventId,
        Instant generatedAt,
        List<TicketDatasetEntryDto> tickets) implements Serializable {
}
