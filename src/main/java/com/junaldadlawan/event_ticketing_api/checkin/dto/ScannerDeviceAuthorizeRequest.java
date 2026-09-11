package com.junaldadlawan.event_ticketing_api.checkin.dto;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/** Matches openapi.yaml's {@code POST /events/{eventId}/scanner-devices} request body. */
public record ScannerDeviceAuthorizeRequest(
        @NotBlank String deviceLabel,
        Boolean forceReplace) implements Serializable {

    public boolean forceReplaceOrDefault() {
        return forceReplace != null && forceReplace;
    }
}
