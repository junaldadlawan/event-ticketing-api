package com.junaldadlawan.event_ticketing_api.notification.repository;

import com.junaldadlawan.event_ticketing_api.notification.entity.NotificationPreference;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, NotificationPreference.Key> {

    List<NotificationPreference> findByUserId(UUID userId);

    /** Of these users, the ones who turned this type off. */
    @Query("select p.userId from NotificationPreference p where p.type = :type and p.enabled = false and p.userId in :userIds")
    List<UUID> findOptedOut(@Param("type") NotificationType type, @Param("userIds") Collection<UUID> userIds);
}
