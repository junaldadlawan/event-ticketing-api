package com.junaldadlawan.event_ticketing_api.checkin.history;

import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInSourceType;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** One scan attempt of an event, without any buyer or owner detail. {@code ticketId} is null when the code matched no ticket (such attempts are not stored today). */
public record CheckInHistoryItem(
        UUID id,
        UUID ticketId,
        String ticketNumber,
        UUID ticketTypeId,
        String ticketTypeName,
        CheckInSourceType sourceType,
        UUID sourceId,
        String scannerName,
        String scannerLocation,
        Instant scannedAt,
        CheckInResult result) implements Serializable {
}
