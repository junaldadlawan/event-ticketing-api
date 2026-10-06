package com.junaldadlawan.event_ticketing_api.event.service;

import com.junaldadlawan.event_ticketing_api.common.config.CacheConfig;
import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.dto.EventResponse;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Cached read side of the PUBLIC event endpoints. A separate bean from
 * {@link EventService} so the caching proxy actually applies (a call from
 * within the same class would bypass it).
 * <p>
 * Caches response DTOs, never entities: an entity cached past its session
 * would throw on its lazy collections, and the DTO already embeds the venue
 * snapshot so a cache hit costs zero queries (no per-event venue lookup).
 * Authorization is unchanged: only data that is public anyway is ever cached -
 * a draft event is never cached or served from here.
 */
@Service
@RequiredArgsConstructor
public class EventQueryService {

    private final EventRepository eventRepository;
    private final EventService eventService;

    /**
     * Public event detail. Returns {@code null} for a DRAFT event: null is
     * never cached ({@code unless}), and the caller falls back to the normal
     * authorized path ({@link EventService#getEvent}). A missing/deleted event
     * throws 404, which is not cached either.
     */
    @Cacheable(cacheNames = CacheConfig.PUBLIC_EVENT, key = "#eventId", unless = "#result == null")
    public EventResponse findPublic(UUID eventId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
        if (event.getStatus() == EventStatus.DRAFT) {
            return null;
        }
        return toResponse(event);
    }

    /** Public listing (PUBLISHED, non-deleted). Key covers every filter and the page/size/sort. */
    @Cacheable(cacheNames = CacheConfig.PUBLIC_EVENT_SEARCH,
            key = "#category + '|' + #keyword + '|' + #startsAfter + '|' + #startsBefore"
                    + " + '|' + #pageable.pageNumber + '|' + #pageable.pageSize + '|' + #pageable.sort")
    public PageResponse<EventResponse> searchPublic(String category, String keyword, Instant startsAfter,
                                                    Instant startsBefore, Pageable pageable) {
        return PageResponse.from(
                eventService.listEvents(category, keyword, startsAfter, startsBefore, pageable)
                        .map(this::toResponse));
    }

    private EventResponse toResponse(Event event) {
        return EventResponse.from(event, eventService.resolveVenue(event.getVenueId()));
    }
}
