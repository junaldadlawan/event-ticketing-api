package com.junaldadlawan.event_ticketing_api.moderation.dto;

import com.junaldadlawan.event_ticketing_api.moderation.entity.ModerationAction;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationActionType;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationTargetType;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** DTO for {@link ModerationAction}. */
public record ModerationActionResponse(
        UUID id,
        ModerationTargetType targetType,
        UUID targetId,
        ModerationActionType action,
        String reason,
        String previousStatus,
        UUID performedBy,
        Instant createdAt) implements Serializable {

    public static ModerationActionResponse from(ModerationAction action) {
        return new ModerationActionResponse(
                action.getId(),
                action.getTargetType(),
                action.getTargetId(),
                action.getAction(),
                action.getReason(),
                action.getPreviousStatus(),
                action.getPerformedBy(),
                action.getCreatedAt());
    }
}
