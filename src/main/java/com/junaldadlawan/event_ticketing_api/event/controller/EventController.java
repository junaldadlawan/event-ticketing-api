package com.junaldadlawan.event_ticketing_api.event.controller;

import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.event.dto.EventRequest;
import com.junaldadlawan.event_ticketing_api.event.dto.EventResponse;
import com.junaldadlawan.event_ticketing_api.event.dto.EventUpdateRequest;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.service.EventCompletionService;
import com.junaldadlawan.event_ticketing_api.event.service.EventQueryService;
import com.junaldadlawan.event_ticketing_api.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final EventQueryService eventQueryService;
    private final EventCompletionService eventCompletionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventRequest request
    ) {
        return toResponse(eventService.createEvent(request));
    }

    @GetMapping
    public PageResponse<EventResponse> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Instant startsAfter,
            @RequestParam(required = false) Instant startsBefore,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // Cached (see EventQueryService): the public list is the same for every caller.
        PageResponse<EventResponse> page = eventQueryService.searchPublic(category, keyword, startsAfter, startsBefore, pageable);
        // An event that ended since the last completion sweep would still be listed as PUBLISHED: complete those
        // now (which clears the cache) and list again, so a finished event never shows as live.
        List<UUID> ended = page.content().stream()
                .filter(EventCompletionService::showsLiveAfterEnd).map(EventResponse::id).toList();
        if (!ended.isEmpty()) {
            eventCompletionService.completeEnded(ended);
            page = eventQueryService.searchPublic(category, keyword, startsAfter, startsBefore, pageable);
        }
        return page;
    }

    /**
     * Management listing for admin / organization owner / organizer: every
     * status, scoped to the organizations the caller manages (an admin sees
     * all). Authenticated at the HTTP layer (SecurityConfig), unlike the
     * public {@link #search} above.
     */
    @GetMapping("/managed")
    public PageResponse<EventResponse> managed(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) Instant startsAfter,
            @RequestParam(required = false) Instant startsBefore,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return PageResponse.from(
                eventService.listManagedEvents(category, keyword, status, organizationId, startsAfter, startsBefore, pageable)
                        .map(this::toResponse)
        );
    }

    @GetMapping("/{eventId}")
    public EventResponse get(@PathVariable UUID eventId) {
        // Cached for non-draft events (public anyway). A DRAFT returns null here and
        // falls through to the normal authorized path, so a draft is never cached or
        // served from the cache.
        // One still shown as live after its end time goes through getEvent too, which completes it.
        EventResponse publicEvent = eventQueryService.findPublic(eventId);
        if (publicEvent != null && !EventCompletionService.showsLiveAfterEnd(publicEvent)) {
            return publicEvent;
        }
        return toResponse(eventService.getEvent(eventId));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID eventId) {
        eventService.delete(eventId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{eventId}")
    public EventResponse update(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventUpdateRequest request) {
        return toResponse(eventService.updateEvent(eventId, request));
    }

    @PostMapping("/{eventId}/publish")
    public EventResponse publish(@PathVariable UUID eventId) {
        return toResponse(eventService.publishEvent(eventId));
    }

    @PostMapping("/{eventId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventResponse cancel(@PathVariable UUID eventId) {
        return toResponse(eventService.cancelEvent(eventId));
    }

    private EventResponse toResponse(Event event) {
        return EventResponse.from(event, eventService.resolveVenue(event.getVenueId()));
    }
}
