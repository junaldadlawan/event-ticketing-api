package com.junaldadlawan.event_ticketing_api.event.controller;

import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.event.dto.EventRequest;
import com.junaldadlawan.event_ticketing_api.event.dto.EventResponse;
import com.junaldadlawan.event_ticketing_api.event.dto.EventUpdateRequest;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.event.service.EventService;
import jakarta.servlet.ServletResponse;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Builder
public class EventController {

    private final EventService eventService;
    private final EventRepository eventRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventRequest request
    ) {
        return EventResponse.from(eventService.createEvent(request));
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
                        .map(EventResponse::from)
        );
    }

    @DeleteMapping("/{eventId}")
//    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID eventId) {
        eventService.delete(eventId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{eventId}")
    public EventResponse update(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventUpdateRequest request) {
        return EventResponse.from(eventService.updateEvent(eventId, request));
    }
}
