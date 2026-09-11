package com.junaldadlawan.event_ticketing_api.notification.dto;

import com.junaldadlawan.event_ticketing_api.notification.entity.Notification;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationChannel;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationStatus;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** DTO for {@link Notification}, matching openapi.yaml's {@code Notification} schema. */
public record NotificationResponse(
        UUID id,
        NotificationType type,
        NotificationChannel channel,
        String relatedObjectType,
        UUID relatedObjectId,
        NotificationStatus status,
        Instant sentAt,
        Instant createdAt) implements Serializable {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getChannel(),
                notification.getRelatedObjectType(),
                notification.getRelatedObjectId(),
                notification.getStatus(),
                notification.getSentAt(),
                notification.getCreatedAt());
    }
}
