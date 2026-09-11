package com.junaldadlawan.event_ticketing_api.checkin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/** Matches openapi.yaml's {@code POST /check-in/fallback-scans} request body. */
public record FallbackScanBatchRequest(
        @NotNull
        UUID eventId,

        @NotEmpty
        @Valid
        List<FallbackScanItemDto> scans) implements Serializable {
}
