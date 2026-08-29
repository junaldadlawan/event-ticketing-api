package com.junaldadlawan.event_ticketing_api.event.service;

import com.junaldadlawan.event_ticketing_api.event.dto.EventRequest;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface EventService {
    Event createEvent(EventRequest request);
    Page<Event> listEvents(String category, String keyword, Instant from, Instant to, Pageable pageable);
    void delete(UUID id);
}

