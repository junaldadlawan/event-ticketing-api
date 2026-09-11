package com.junaldadlawan.event_ticketing_api.notification.repository;

import com.junaldadlawan.event_ticketing_api.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** {@code GET /users/me/notifications} - the caller's own notifications, most recent first. */
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
