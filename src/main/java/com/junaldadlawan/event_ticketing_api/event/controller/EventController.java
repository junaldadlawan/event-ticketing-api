package com.junaldadlawan.event_ticketing_api.event.controller;

import com.junaldadlawan.event_ticketing_api.event.dto.EventRequest;
import com.junaldadlawan.event_ticketing_api.event.dto.EventResponse;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.service.EventService;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Builder
public class EventController {

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(
            @Valid @RequestBody EventRequest request
    ) {
        return EventResponse.from(Event.builder()
                .title("Java Conference 2026")
                .category("CONFERENCE1")
                .venue(1)
                .startAt(Instant.now())
                .endAt(Instant.now())
                .ticketPrefix("JAVA")
                .build());

//        return EventResponse.from(Event.builder()
//                .title("Java Conference 2026")
//                .category("CONFERENCE")
//                .venue(1)
//                .startAt(Instant.now())
//                .endAt(Instant.now())
//                .ticketPrefix("JAVA")
//                .build());
    }
}
