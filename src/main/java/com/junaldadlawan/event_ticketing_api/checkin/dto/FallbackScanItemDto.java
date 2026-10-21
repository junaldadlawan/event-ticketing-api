package com.junaldadlawan.event_ticketing_api.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;

/** One entry in {@code FallbackScanBatchRequest.scans}, matching openapi.yaml's request item schema. */
public record FallbackScanItemDto(
        @NotBlank
        String rawCredential,

        @NotNull
        Instant capturedAt,

        // where this scan was made; left out = the device's current location
        @Size(max = 100)
        String location) implements Serializable {
}
