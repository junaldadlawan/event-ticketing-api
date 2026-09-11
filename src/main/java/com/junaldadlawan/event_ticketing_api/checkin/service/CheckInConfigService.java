package com.junaldadlawan.event_ticketing_api.checkin.service;

import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInConfigResponse;
import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInConfigUpdateRequest;

import java.util.UUID;

public interface CheckInConfigService {

    /** {@code GET /events/{eventId}/check-in-config} - any authenticated caller (user or scanner device, BR-CHECKIN-011). */
    CheckInConfigResponse get(UUID eventId);

    /** {@code PATCH /events/{eventId}/check-in-config} - owning organizer/owner/admin. Upserts. */
    CheckInConfigResponse update(UUID eventId, CheckInConfigUpdateRequest request);
}
