package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.repository.FunnelRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * {@code POST /events/{eventId}/views} (BR-ANALYTICS-003): records that a visitor opened a public event page. Whatever happens the caller gets
 * 204, so the endpoint never tells whether an event exists, was counted or was refused.
 */
@Service
@RequiredArgsConstructor
public class EventViewServiceImpl implements EventViewService {

    static final Duration DEDUP_WINDOW = Duration.ofMinutes(30);
    /** Events a visitor can actually open; any other (or unknown) event is silently not counted. */
    static final Set<EventStatus> COUNTED_STATUSES = EnumSet.of(EventStatus.PUBLISHED, EventStatus.ON_SALE, EventStatus.SOLD_OUT, EventStatus.COMPLETED);
    static final int MAX_VISITOR_ID_LENGTH = 100;

    private final EventRepository eventRepository;
    private final FunnelRepository funnelRepository;
    private final OrganizationAccessGuard accessGuard;
    private final ViewRateLimiter rateLimiter;


    @Override
    public boolean recordView(UUID eventId, String visitorId, String clientKey) {
        if (!rateLimiter.tryAcquire(clientKey)) {
            return false;
        }
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId).orElse(null);
        if (event == null || !COUNTED_STATUSES.contains(event.getStatus())) {
            return false;
        }
        UUID userId = currentUserIdOrNull();
        String visitorKey;
        if (userId != null) {
            // the event's own team and admins are not audience
            if (accessGuard.isAdmin() || accessGuard.hasRole(userId, event.getOrganizationId(), OrganizationRole.OWNER)
                    || accessGuard.hasRole(userId, event.getOrganizationId(), OrganizationRole.ORGANIZER)) {
                return false;
            }
            visitorKey = userId.toString();
        } else if (visitorId != null && !visitorId.isBlank() && visitorId.length() <= MAX_VISITOR_ID_LENGTH) {
            visitorKey = sha256(visitorId.trim());
        } else {
            return false;   // nothing to tell one anonymous visitor from another
        }
        Instant now = Instant.now();
        return funnelRepository.insertViewUnlessRecent(eventId, visitorKey, userId, now, now.minus(DEDUP_WINDOW));
    }

    private UUID currentUserIdOrNull() {
        try {
            return accessGuard.currentUserId();
        } catch (ForbiddenException e) {
            return null;
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
