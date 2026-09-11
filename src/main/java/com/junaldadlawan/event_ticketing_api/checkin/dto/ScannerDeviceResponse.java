package com.junaldadlawan.event_ticketing_api.checkin.dto;

import com.junaldadlawan.event_ticketing_api.checkin.entity.ScannerDevice;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link ScannerDevice} INCLUDING its raw credential - matches
 * openapi.yaml's {@code ScannerDeviceWithCredential} schema, returned only
 * once, from {@code POST /events/{eventId}/scanner-devices} itself. No
 * plain (credential-less) {@code ScannerDeviceResponse} exists because
 * openapi defines no {@code GET} for a single device or device list - the
 * credential is genuinely never shown again after creation, matching its
 * own schema description.
 */
public record ScannerDeviceResponse(
        UUID id,
        UUID eventId,
        String deviceLabel,
        ScannerDeviceStatus status,
        String credential,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) implements Serializable {

    public static ScannerDeviceResponse from(ScannerDevice device, String credential) {
        return new ScannerDeviceResponse(
                device.getId(),
                device.getEventId(),
                device.getDeviceLabel(),
                device.getStatus(),
                credential,
                device.getCreatedAt(),
                device.getCreatedBy(),
                device.getUpdatedAt(),
                device.getUpdatedBy());
    }
}
