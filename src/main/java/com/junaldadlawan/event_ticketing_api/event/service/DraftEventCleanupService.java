package com.junaldadlawan.event_ticketing_api.event.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.event.cache.PublicEventCacheEvictionListener;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.upload.service.UploadedFileUrls;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.repository.NotificationRepository;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.platformsetting.service.DraftEventSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BR-EVENT-007: an event still in DRAFT whose start is less than the cutoff away (one hour by default, an admin setting) or already past is
 * soft deleted, exactly like the manual delete, so its history and audit log stay. The organization's owner and its organizers with the EVENTS
 * permission are told, and 24 hours before the cutoff they get a warning.
 * <p>
 * Safe to run again and on several instances: the delete is a conditional UPDATE (whoever gets 1 back records the audit entry and sends the
 * notifications), the notifications are once per person and event (unique index), and every event is handled on its own so one failure does not
 * stop the rest. The first run happens shortly after startup, which rechecks every draft that already meets the rule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftEventCleanupService {

    public static final String AUTO_DELETED_ACTION = "event.auto_deleted_unpublished";
    private static final Duration WARN_BEFORE_CUTOFF = Duration.ofHours(24);
    private static final int BATCH = 100;
    private static final int MAX_BATCHES = 50;
    private static final DateTimeFormatter WITH_ZONE = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a z", Locale.US);

    private final EventRepository eventRepository;
    private final PublicEventCacheEvictionListener cacheEviction;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final DraftEventSettingsService settingsService;
    private final UploadedFileUrls uploadedFileUrls;

    /** What one run did. */
    public record Result(int deleted, int warned) {
    }

    @Scheduled(fixedDelayString = "${app.events.draft-cleanup-interval:PT1M}",
            initialDelayString = "${app.events.draft-cleanup-initial-delay:PT20S}")
    public void sweep() {
        try {
            Result result = run(Instant.now());
            if (result.deleted() > 0 || result.warned() > 0) {
                log.info("Draft events: deleted {} unpublished draft(s), warned about {}", result.deleted(), result.warned());
            }
        } catch (RuntimeException e) {
            // the next run retries; a failed run must not stop the scheduler thread
            log.warn("Draft event cleanup failed: {}", e.toString());
        }
    }

    /** Deletes and warns for what is due at {@code now}. */
    public Result run(Instant now) {
        return run(now, null);
    }

    /** {@link #run(Instant)} limited to these events (null = all), so a test touches only its own. */
    public Result run(Instant now, Set<UUID> onlyEvents) {
        int minutes = settingsService.current().getAutoDeleteMinutesBeforeStart();
        if (minutes <= 0) {
            return new Result(0, 0);   // the rule is switched off
        }
        Instant cutoff = now.plus(Duration.ofMinutes(minutes));
        int deleted = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<UUID> ids = eventRepository.findDraftIdsStartingBy(cutoff, PageRequest.of(0, BATCH));
            int done = 0;
            for (UUID id : ids) {
                if (onlyEvents != null && !onlyEvents.contains(id)) {
                    continue;
                }
                try {
                    if (deleteIfDue(id, cutoff, now, minutes)) {
                        done++;
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not auto-delete draft event {}: {}", id, e.toString());
                }
            }
            deleted += done;
            if (ids.size() < BATCH || done == 0) {
                break;   // nothing left, or nothing in this batch could be deleted (do not loop on the same ones)
            }
        }
        int warned = 0;
        for (Event event : eventRepository.findDraftsStartingBetween(cutoff, cutoff.plus(WARN_BEFORE_CUTOFF))) {
            if (onlyEvents != null && !onlyEvents.contains(event.getId())) {
                continue;
            }
            try {
                warned += warn(event, minutes);
            } catch (RuntimeException e) {
                log.warn("Could not warn about draft event {}: {}", event.getId(), e.toString());
            }
        }
        return new Result(deleted, warned);
    }

    private boolean deleteIfDue(UUID eventId, Instant cutoff, Instant now, int minutes) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            return false;
        }
        // the conditional UPDATE is the claim: still a draft, not deleted, still due; 0 = another instance (or the organizer) got there first
        if (eventRepository.softDeleteDraftIfDue(eventId, cutoff, now) == 0) {
            return false;
        }
        cacheEviction.onChange(eventId);
        String reason = "Not published at least " + describe(minutes) + " before it started";
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", event.getTitle());
        details.put("startAt", event.getStartAt().toString());
        details.put("reason", reason);
        // actor null = the system, not a user
        auditLogService.record(null, AUTO_DELETED_ACTION, "Event", eventId, eventId, event.getOrganizationId(), details);
        String message = event.getTitle() + " was deleted because it was not published at least " + describe(minutes) + " before it started.";
        notifyTeam(event, NotificationType.EVENT_DRAFT_DELETED, message);
        eventRepository.findImageUrls(eventId).forEach(uploadedFileUrls::deleteIfUnreferenced);
        return true;
    }

    private int warn(Event event, int minutes) {
        Instant deleteAt = event.getStartAt().minus(Duration.ofMinutes(minutes));
        String message = event.getTitle() + " is still a draft and will be deleted at "
                + WITH_ZONE.format(deleteAt.atZone(ZoneId.of(event.getTimezone()))) + " unless you publish it.";
        return notifyTeam(event, NotificationType.EVENT_DRAFT_WARNING, message);
    }

    /** The owner and the organizers with the EVENTS permission, once each per type and event. Returns how many were sent. */
    private int notifyTeam(Event event, NotificationType type, String message) {
        Set<UUID> already = Set.copyOf(notificationRepository.findNotifiedUserIds(event.getId(), type));
        Set<UUID> recipients = organizationMemberRepository.findByOrganizationId(event.getOrganizationId()).stream()
                .filter(member -> OrganizationAccessGuard.permissionsOf(member).contains(OrganizationPermission.EVENTS))
                .map(OrganizationMember::getUserId)
                .filter(userId -> !already.contains(userId))
                .collect(Collectors.toSet());
        for (UUID userId : recipients) {
            notificationService.notifyAboutEvent(userId, type, event.getId(), message, null);
        }
        return recipients.size();
    }

    /** "1 hour", "3 hours" or "90 minutes". */
    static String describe(int minutes) {
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours == 1 ? "1 hour" : hours + " hours";
        }
        return minutes + " minutes";
    }
}
