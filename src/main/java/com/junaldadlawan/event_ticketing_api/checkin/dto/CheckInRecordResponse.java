package com.junaldadlawan.event_ticketing_api.checkin.dto;

import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInRecord;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInSourceType;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** DTO for {@link CheckInRecord}, matching openapi.yaml's {@code CheckInRecord} schema. */
public record CheckInRecordResponse(
        UUID id,
        UUID ticketId,
        CheckInSourceType sourceType,
        UUID sourceId,
        Instant scannedAt,
        CheckInResult result) implements Serializable {

    public static CheckInRecordResponse from(CheckInRecord record) {
        return new CheckInRecordResponse(
                record.getId(),
                record.getTicketId(),
                record.getSourceType(),
                record.getSourceId(),
                record.getScannedAt(),
                record.getResult());
    }
}
