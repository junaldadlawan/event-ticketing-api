package com.junaldadlawan.event_ticketing_api.notification.service;

import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.repository.NotificationRepository;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * BR-NOTIFY-002: event lifecycle notifications to the event's current ticket holders (VALID or USED tickets; owners,
 * not buyers). The scheduled ones - reminder (24h before), gates open (the event's gate time), starting, review request (at the end) - come
 * from {@link #sweep}, once per user per event (the unique index on notifications backs this up), never for a draft,
 * cancelled or deleted event, and never when their time is before the event's {@code notificationsFrom} (set on
 * publish and when it moves), so a time already passed is not sent late. EVENT_CHANGE comes from {@link #eventChanged}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventLifecycleNotifier {

    private static final Set<EventStatus> NOTIFIABLE = EnumSet.of(EventStatus.PUBLISHED, EventStatus.ON_SALE,
            EventStatus.SOLD_OUT, EventStatus.COMPLETED);
    private static final Set<TicketStatus> HELD = EnumSet.of(TicketStatus.VALID, TicketStatus.USED);
    private static final Duration REMINDER_BEFORE = Duration.ofHours(24);
    // shortcut: a review request still goes out up to a week after the end (e.g. after downtime); later than that it is dropped
    private static final Duration REVIEW_REQUEST_WINDOW = Duration.ofDays(7);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter WITH_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US);
    private static final DateTimeFormatter WITHOUT_YEAR = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US);

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final NotificationRepository notificationRepository;
    private final VenueRepository venueRepository;
    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;

    @Scheduled(fixedDelayString = "${app.notifications.lifecycle-interval:PT1M}",
            initialDelayString = "${app.notifications.lifecycle-initial-delay:PT45S}")
    public void sweep() {
        try {
            int sent = run(Instant.now());
            if (sent > 0) {
                log.info("Sent {} event lifecycle notification(s)", sent);
            }
        } catch (RuntimeException e) {
            // the next run retries; a failed run must not stop the scheduler thread
            log.warn("Event lifecycle notification run failed: {}", e.toString());
        }
    }

    /** Sends whatever is due at {@code now}; returns how many were sent. Safe to run again (and on several instances). */
    public int run(Instant now) {
        return run(now, null);
    }

    /** {@link #run(Instant)} limited to these events (null = all) - so a test with its own clock touches only its own. */
    int run(Instant now, Set<UUID> onlyEvents) {
        int sent = 0;
        for (Event event : eventRepository.findLifecycleCandidates(NOTIFIABLE, now.plus(REMINDER_BEFORE), now.minus(REVIEW_REQUEST_WINDOW))) {
            if (onlyEvents != null && !onlyEvents.contains(event.getId())) {
                continue;
            }
            Instant start = event.getStartAt();
            Instant end = event.getEndAt();
            if (now.isBefore(start) && due(event, start.minus(REMINDER_BEFORE), now)) {
                sent += send(event, NotificationType.EVENT_REMINDER, ticketRepository.findHolderIds(event.getId(), HELD),
                        event.getTitle() + " is tomorrow at " + TIME.format(start.atZone(zone(event))) + ".");
            }
            if (now.isBefore(end)) {
                Integer gateMinutes = event.getGateOpensMinutesBefore();
                if (gateMinutes != null && due(event, start.minus(Duration.ofMinutes(gateMinutes)), now)) {
                    sent += send(event, NotificationType.GATE_OPEN, ticketRepository.findHolderIds(event.getId(), HELD),
                            "Gates are open for " + event.getTitle() + ".");
                }
                if (due(event, start, now)) {
                    sent += send(event, NotificationType.EVENT_STARTING, ticketRepository.findHolderIds(event.getId(), HELD),
                            event.getTitle() + " is starting now.");
                }
            }
            if (due(event, end, now)) {
                sent += send(event, NotificationType.REVIEW_REQUEST, ticketRepository.findAttendeesWithoutReview(event.getId()),
                        "How was " + event.getTitle() + "? Rate it.");
            }
        }
        return sent;
    }

    /**
     * EVENT_CHANGE, right away and once per save: called by the event update after saving, with the values from before
     * it. Sends nothing unless the start, end, timezone or venue changed. The event's {@code notificationsFrom} moves to
     * now, so reminders whose new time has already passed are not sent.
     */
    public void eventChanged(Event event, Instant oldStart, Instant oldEnd, String oldTimezone, UUID oldVenueId) {
        List<String> changes = new ArrayList<>();
        ZoneId newZone = zone(event);
        ZoneId oldZone = ZoneId.of(oldTimezone);
        if (!event.getStartAt().equals(oldStart)) {
            changes.add("starts " + WITH_YEAR.format(event.getStartAt().atZone(newZone)) + " (was " + WITHOUT_YEAR.format(oldStart.atZone(oldZone)) + ")");
        }
        if (!event.getEndAt().equals(oldEnd)) {
            changes.add("ends " + WITH_YEAR.format(event.getEndAt().atZone(newZone)) + " (was " + WITHOUT_YEAR.format(oldEnd.atZone(oldZone)) + ")");
        }
        if (!Objects.equals(event.getVenueId(), oldVenueId)) {
            changes.add("venue " + venueName(event.getVenueId()) + " (was " + venueName(oldVenueId) + ")");
        }
        if (!event.getTimezone().equals(oldTimezone)) {
            changes.add("timezone " + event.getTimezone() + " (was " + oldTimezone + ")");
        }
        if (changes.isEmpty() || event.getDeletedAt() != null || !NOTIFIABLE.contains(event.getStatus())) {
            return;
        }
        Instant now = Instant.now();
        event.setNotificationsFrom(now);
        eventRepository.save(event);
        String message = event.getTitle() + " moved: " + String.join("; ", changes) + ".";
        for (UUID userId : preferenceService.wanting(ticketRepository.findHolderIds(event.getId(), HELD), NotificationType.EVENT_CHANGE)) {
            trySend(userId, NotificationType.EVENT_CHANGE, event.getId(), message, now);
        }
    }

    private static boolean due(Event event, Instant at, Instant now) {
        return !at.isAfter(now) && (event.getNotificationsFrom() == null || !at.isBefore(event.getNotificationsFrom()));
    }

    /** To each of {@code userIds} who has not had this type for the event yet and still wants it. */
    private int send(Event event, NotificationType type, Collection<UUID> userIds, String message) {
        Set<UUID> recipients = new HashSet<>(userIds);
        recipients.removeAll(notificationRepository.findNotifiedUserIds(event.getId(), type));
        int sent = 0;
        for (UUID userId : preferenceService.wanting(recipients, type)) {
            sent += trySend(userId, type, event.getId(), message, null) ? 1 : 0;
        }
        return sent;
    }

    private boolean trySend(UUID userId, NotificationType type, UUID eventId, String message, Instant sentFor) {
        try {
            notificationService.notifyAboutEvent(userId, type, eventId, message, sentFor);
            return true;
        } catch (RuntimeException e) {
            // another instance sent it first (the unique index refused this one), or the send failed: the others go on
            log.debug("Lifecycle notification not sent (userId={}, type={}, eventId={}): {}", userId, type, eventId, e.toString());
            return false;
        }
    }

    private String venueName(UUID venueId) {
        return venueId == null ? "online" : venueRepository.findById(venueId).map(Venue::getName).orElse("an unknown venue");
    }

    private static ZoneId zone(Event event) {
        return ZoneId.of(event.getTimezone());
    }
}
