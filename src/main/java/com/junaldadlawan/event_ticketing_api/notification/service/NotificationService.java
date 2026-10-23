package com.junaldadlawan.event_ticketing_api.notification.service;

import com.junaldadlawan.event_ticketing_api.notification.dto.NotificationResponse;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;

import java.time.Instant;
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

    /** A notification with its own text (also the email body), about a record that is not an event. Never throws, like {@link #notify}. */
    void notifyWithMessage(UUID userId, NotificationType type, String relatedObjectType, UUID relatedObjectId, String message);

    /**
     * BR-NOTIFY-002: a notification about an event, with its server-composed {@code message} (also the email body).
     * {@code sentFor} tells EVENT_CHANGE notifications apart (the save's time); null for the once-per-event types.
     * Never throws, like {@link #notify}; skipped when the user turned the type off.
     */
    void notifyAboutEvent(UUID userId, NotificationType type, UUID eventId, String message, Instant sentFor);

    /** {@code GET /users/me/notifications} - the caller's own notifications, most recent first. */
    List<NotificationResponse> listMyNotifications();
}
