package com.junaldadlawan.event_ticketing_api.checkin.dto;

import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInMode;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link CheckInConfig}, matching openapi.yaml's {@code
 * CheckInConfig} schema. No {@code CheckInConfig} row is created for an
 * event until an organizer first PATCHes one - {@link #defaultFor}
 * synthesizes a {@code STANDARD}/300s default (the more common assumption
 * for an event generally, and openapi's own documented default for the
 * expiry field), same idiom as {@code ResalePolicyResponse}/{@code
 * RefundPolicyResponse}.
 */
public record CheckInConfigResponse(
        UUID eventId,
        CheckInMode mode,
        int offlineFallbackExpirySeconds,
        String warning,
        Instant createdAt,
        Instant updatedAt,
        String updatedBy) implements Serializable {

    public static CheckInConfigResponse from(CheckInConfig config) {
        return from(config, null);
    }

    public static CheckInConfigResponse from(CheckInConfig config, String warning) {
        return new CheckInConfigResponse(
                config.getEventId(),
                config.getMode(),
                config.getOfflineFallbackExpirySeconds(),
                warning,
                config.getCreatedAt(),
                config.getUpdatedAt(),
                config.getUpdatedBy());
    }

    public static CheckInConfigResponse defaultFor(UUID eventId) {
        return new CheckInConfigResponse(eventId, CheckInMode.STANDARD, 300, null, null, null, null);
    }
}
