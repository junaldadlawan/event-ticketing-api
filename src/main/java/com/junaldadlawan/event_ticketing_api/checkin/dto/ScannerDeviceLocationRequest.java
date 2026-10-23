package com.junaldadlawan.event_ticketing_api.checkin.dto;

import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** {@code PATCH /scanner-devices/{deviceId}}: move the device, e.g. to "Gate B". Null or blank clears it. */
public record ScannerDeviceLocationRequest(
        @Size(max = 100) String location) implements Serializable {

    /** Trimmed; blank means none. */
    public String locationOrNull() {
        return ScannerDeviceAuthorizeRequest.blankToNull(location);
    }
}
