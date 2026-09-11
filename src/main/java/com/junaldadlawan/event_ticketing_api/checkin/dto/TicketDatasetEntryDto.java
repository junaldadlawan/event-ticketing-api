package com.junaldadlawan.event_ticketing_api.checkin.dto;

import java.io.Serializable;
import java.util.UUID;

/**
 * One ticket's offline-validation data, matching openapi.yaml's nested
 * {@code TicketDataset.tickets[]} item. {@code credentialHash} is a SHA-256
 * digest of the ticket's raw credential - "for local signature/lookup
 * verification without exposing the raw credential unnecessarily" (openapi's
 * own description): an offline client hashes a freshly-scanned credential
 * and looks for a match in this pre-fetched list, never needing the
 * server's HMAC secret itself.
 */
public record TicketDatasetEntryDto(
        UUID ticketId,
        String credentialHash,
        String status) implements Serializable {
}
