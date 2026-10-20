package com.junaldadlawan.event_ticketing_api.event.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.event.cache.PublicEventCacheEvictionListener;
import com.junaldadlawan.event_ticketing_api.event.dto.EventResponse;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * BR-EVENT-005: an event whose end time has passed is over. A scheduled sweep moves live events
 * (PUBLISHED / ON_SALE / SOLD_OUT) to COMPLETED, and the reads that show an event's status complete it on the
 * spot so it is right between sweeps. A completed (or already ended) event is read-only: see {@link #requireNotEnded}.
 * <p>
 * The status change is a conditional UPDATE, not an entity save: it bypasses the entity listener, so the public
 * event caches are cleared here, and it can never overwrite a concurrent change to the event's other columns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCompletionService {

    public static final String ENDED_MESSAGE = "Event has ended";
    private static final Set<EventStatus> LIVE_STATUSES =
            EnumSet.of(EventStatus.PUBLISHED, EventStatus.ON_SALE, EventStatus.SOLD_OUT);

    private final EventRepository eventRepository;
    private final PublicEventCacheEvictionListener cacheEviction;
    private final AuditLogService auditLogService;

    /** COMPLETED, or past its end time whatever its status says (the sweep may not have run yet). */
    public static boolean hasEnded(Event event) {
        return event.getStatus() == EventStatus.COMPLETED || !event.getEndAt().isAfter(Instant.now());
    }

    /** 409 for any write to an event that has ended. Call after the 404 and permission checks. */
    public static void requireNotEnded(Event event) {
        if (hasEnded(event)) {
            throw new ConflictException(ENDED_MESSAGE);
        }
    }

    /** A cached response that still shows a live status after the end time: the caller re-reads it through the service. */
    public static boolean showsLiveAfterEnd(EventResponse response) {
        return LIVE_STATUSES.contains(response.status()) && !response.endAt().isAfter(Instant.now());
    }

    /** Completes this event if it is live and has ended; returns it with the status it now has. */
    public Event completeIfEnded(Event event) {
        if (LIVE_STATUSES.contains(event.getStatus()) && !event.getEndAt().isAfter(Instant.now())
                && complete(event.getId())) {
            event.setStatus(EventStatus.COMPLETED);
        }
        return event;
    }

    /** Completes every live event that has ended. Returns how many it completed. */
    public int completeEndedEvents() {
        return completeEnded(eventRepository.findEndedIds(LIVE_STATUSES, Instant.now()));
    }

    /** Completes those of these events that are live and have ended (the rest are left alone). Returns how many. */
    public int completeEnded(Collection<UUID> eventIds) {
        int completed = 0;
        for (UUID eventId : eventIds) {
            if (complete(eventId)) {
                completed++;
            }
        }
        return completed;
    }

    @Scheduled(fixedDelayString = "${app.events.completion-sweep-interval:PT1M}",
            initialDelayString = "${app.events.completion-sweep-initial-delay:PT30S}")
    public void sweep() {
        try {
            int completed = completeEndedEvents();
            if (completed > 0) {
                log.info("Completed {} ended event(s)", completed);
            }
        } catch (RuntimeException e) {
            // the next run retries; a failed sweep must not stop the scheduler thread
            log.warn("Event completion sweep failed: {}", e.toString());
        }
    }

    private boolean complete(UUID eventId) {
        if (eventRepository.markCompletedIfEnded(eventId, LIVE_STATUSES, EventStatus.COMPLETED, Instant.now()) == 0) {
            return false;   // someone else completed it first, or it changed status meanwhile
        }
        cacheEviction.onChange(eventId);
        // actor null = the system completed it, not whoever happened to read the event at that moment
        auditLogService.record(null, "event.completed", "Event", eventId, eventId);
        return true;
    }
}
