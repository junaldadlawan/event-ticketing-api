package com.junaldadlawan.event_ticketing_api.dispute.dto;

import com.junaldadlawan.event_ticketing_api.dispute.enums.DisputeStatus;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Matches openapi.yaml's {@code DisputeUpdate} schema. Both fields optional
 * (partial update, {@code null} = unchanged) - admin only, see {@code
 * DisputeServiceImpl.update}. Mirrors {@code RefundPolicyUpdateRequest}/
 * {@code CheckInConfigUpdateRequest}'s partial-update style.
 */
public record DisputeUpdateRequest(
        DisputeStatus status,

        @Size(max = 1000)
        String resolution) implements Serializable {
}
