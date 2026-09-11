package com.junaldadlawan.event_ticketing_api.notification.service;

import com.junaldadlawan.event_ticketing_api.notification.dto.NotificationResponse;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    /**
     * Persists and attempts delivery of a notification. Per NFR 5.2 ("a
     * notification failure must not roll back a successful payment"), this
     * method never throws - any failure (delivery, or even this method's
     * own persistence) is caught and logged internally, never propagated to
     * the caller. {@code relatedObjectType}/{@code relatedObjectId} may
     * both be null for a notification with no single backing row.
     */
    void notify(UUID userId, NotificationType type, String relatedObjectType, UUID relatedObjectId);

    /** {@code GET /users/me/notifications} - the caller's own notifications, most recent first. */
    List<NotificationResponse> listMyNotifications();
}
