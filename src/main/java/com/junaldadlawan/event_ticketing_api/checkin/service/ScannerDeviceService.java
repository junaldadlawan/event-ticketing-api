package com.junaldadlawan.event_ticketing_api.checkin.service;

import com.junaldadlawan.event_ticketing_api.checkin.dto.ScannerDeviceLocationRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ScannerDeviceAuthorizeRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ScannerDeviceResponse;

import java.util.UUID;

public interface ScannerDeviceService {

    /** {@code POST /events/{eventId}/scanner-devices} - owning organizer/owner/admin. Credential shown once, here. */
    ScannerDeviceResponse authorize(UUID eventId, ScannerDeviceAuthorizeRequest request);

    /** {@code DELETE /scanner-devices/{deviceId}} - owning organizer/owner/admin. */
    void revoke(UUID deviceId);

    /**
     * {@code PATCH /scanner-devices/{deviceId}} - move the device (its location). The device itself (its own id only),
     * or the owning organizer/owner/admin. 409 for a revoked device. The credential is never returned again.
     */
    ScannerDeviceResponse updateLocation(UUID deviceId, ScannerDeviceLocationRequest request);
}
