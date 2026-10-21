package com.junaldadlawan.event_ticketing_api.notification.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.notification.dto.NotificationPreferencesDto;
import com.junaldadlawan.event_ticketing_api.notification.entity.NotificationPreference;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.repository.NotificationPreferenceRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BR-NOTIFY-002: the types a user may turn off. Order confirmations, receipts, refunds, cancellations, dispute
 * outcomes and account moderation are always sent and are not listed here.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    public static final Set<NotificationType> OPTIONAL = EnumSet.of(NotificationType.EVENT_REMINDER, NotificationType.GATE_OPEN,
            NotificationType.EVENT_STARTING, NotificationType.REVIEW_REQUEST, NotificationType.EVENT_CHANGE,
            NotificationType.WAITLIST_AVAILABILITY);

    private final NotificationPreferenceRepository preferenceRepository;
    private final OrganizationAccessGuard accessGuard;

    public NotificationPreferencesDto getMine() {
        return new NotificationPreferencesDto(of(accessGuard.currentUserId()));
    }

    @Transactional
    public NotificationPreferencesDto updateMine(NotificationPreferencesDto request) {
        UUID userId = accessGuard.currentUserId();
        Map<NotificationType, Boolean> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : request.preferences().entrySet()) {
            NotificationType type = OPTIONAL.stream().filter(t -> t.name().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> new BadRequestException("Unknown notification type '" + entry.getKey() + "'; it can be one of " + OPTIONAL));
            if (entry.getValue() == null) {
                throw new BadRequestException("A preference must be true or false: " + entry.getKey());
            }
            changes.put(type, entry.getValue());
        }
        Instant now = Instant.now();
        changes.forEach((type, enabled) -> preferenceRepository.save(new NotificationPreference(userId, type, enabled, now)));
        return new NotificationPreferencesDto(of(userId));
    }

    /** Whether this user still wants this type (always true for the types nobody can turn off). */
    public boolean wants(UUID userId, NotificationType type) {
        return !OPTIONAL.contains(type) || preferenceRepository.findById(new NotificationPreference.Key(userId, type))
                .map(NotificationPreference::isEnabled).orElse(true);
    }

    /** Of these users, those who still want this type. */
    public Set<UUID> wanting(Collection<UUID> userIds, NotificationType type) {
        Set<UUID> wanting = new HashSet<>(userIds);
        if (OPTIONAL.contains(type) && !userIds.isEmpty()) {
            preferenceRepository.findOptedOut(type, userIds).forEach(wanting::remove);
        }
        return wanting;
    }

    private Map<String, Boolean> of(UUID userId) {
        Map<String, Boolean> preferences = new LinkedHashMap<>();
        OPTIONAL.forEach(type -> preferences.put(type.name(), true));
        preferenceRepository.findByUserId(userId).forEach(p -> preferences.put(p.getType().name(), p.isEnabled()));
        return preferences;
    }
}
