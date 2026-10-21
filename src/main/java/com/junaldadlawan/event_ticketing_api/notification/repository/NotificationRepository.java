package com.junaldadlawan.event_ticketing_api.notification.repository;

import com.junaldadlawan.event_ticketing_api.notification.entity.Notification;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** {@code GET /users/me/notifications} - the caller's own notifications, most recent first. */
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** BR-NOTIFY-002: who already got this lifecycle notification for the event. */
    @Query("select n.userId from Notification n where n.eventId = :eventId and n.type = :type")
    List<UUID> findNotifiedUserIds(@Param("eventId") UUID eventId, @Param("type") NotificationType type);
}
