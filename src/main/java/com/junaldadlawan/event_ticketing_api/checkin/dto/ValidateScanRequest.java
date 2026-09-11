package com.junaldadlawan.event_ticketing_api.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.UUID;

/** Matches openapi.yaml's {@code POST /check-in/validate} request body. */
public record ValidateScanRequest(
        @NotBlank
        String credential,

        @NotNull
        UUID deviceId) implements Serializable {
}
