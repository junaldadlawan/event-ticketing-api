package com.junaldadlawan.event_ticketing_api.moderation.dto;

import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationActionType;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.UUID;

/**
 * BR-ADMIN-002. A single unified admin-moderation resource, deliberately
 * not in openapi.yaml/the ERD (original design confirmed this session) -
 * every action (suspend/reinstate/remove against an organization/event/
 * user) is recorded as its own immutable {@code ModerationAction} row,
 * doubling as the reason/audit trail the roadmap calls for.
 */
public record ModerationActionCreateRequest(
        @NotNull
        ModerationTargetType targetType,

        @NotNull
        UUID targetId,

        @NotNull
        ModerationActionType action,

        @NotBlank
        @Size(max = 1000)
        String reason) implements Serializable {
}
