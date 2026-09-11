package com.junaldadlawan.event_ticketing_api.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

/** One entry in {@code FallbackScanBatchRequest.scans}, matching openapi.yaml's request item schema. */
public record FallbackScanItemDto(
        @NotBlank
        String rawCredential,

        @NotNull
        Instant capturedAt) implements Serializable {
}
