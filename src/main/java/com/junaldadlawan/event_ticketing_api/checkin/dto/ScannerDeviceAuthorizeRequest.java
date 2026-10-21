package com.junaldadlawan.event_ticketing_api.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** Matches openapi.yaml's {@code POST /events/{eventId}/scanner-devices} request body. */
public record ScannerDeviceAuthorizeRequest(
        @NotBlank String deviceLabel,
        Boolean forceReplace,
        // where the device stands, e.g. "Gate A" (optional)
        @Size(max = 100) String location) implements Serializable {

    /** Trimmed; blank means none. */
    public String locationOrNull() {
        return blankToNull(location);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public boolean forceReplaceOrDefault() {
        return forceReplace != null && forceReplace;
    }
}
