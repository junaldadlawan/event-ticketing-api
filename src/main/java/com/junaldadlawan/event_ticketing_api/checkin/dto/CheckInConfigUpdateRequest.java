package com.junaldadlawan.event_ticketing_api.checkin.dto;

import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInMode;
import jakarta.validation.constraints.Positive;

import java.io.Serializable;

/** Matches openapi.yaml's {@code PATCH /events/{eventId}/check-in-config} request body. Both fields optional (partial update). */
public record CheckInConfigUpdateRequest(
        CheckInMode mode,
        @Positive Integer offlineFallbackExpirySeconds) implements Serializable {
}
