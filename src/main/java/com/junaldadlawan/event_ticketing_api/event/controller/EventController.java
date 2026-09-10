package com.junaldadlawan.event_ticketing_api.event.controller;

import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.event.dto.EventRequest;
import com.junaldadlawan.event_ticketing_api.event.dto.EventResponse;
import com.junaldadlawan.event_ticketing_api.event.dto.EventUpdateRequest;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

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
        return PageResponse.from(
                eventService.listEvents(category, keyword, startsAfter, startsBefore, pageable)
                        .map(this::toResponse)
        );
    }

    @GetMapping("/{eventId}")
    public EventResponse get(@PathVariable UUID eventId) {
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
