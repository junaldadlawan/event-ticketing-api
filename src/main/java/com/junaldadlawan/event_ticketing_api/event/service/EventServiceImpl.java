package com.junaldadlawan.event_ticketing_api.event.service;


import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.dto.EventRequest;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.event.specification.EventSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;

    @Override
    public Event createEvent(EventRequest request) {
        Event newEvent = Event.builder()
                .organizationId(UUID.randomUUID())
                .title(request.title())
                .description(request.description())
                .category(request.category())
                .venue(request.venue())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .timezone(request.timezone())
                .image(request.image())
                .ticketPrefix(request.ticketPrefix())
                .createdBy("Aldrin")
                .build();
        return eventRepository.save(newEvent);
    }

    @Override
    public Page<Event> listEvents(String category, String keyword, Instant from, Instant to, Pageable pageable) {
        Specification<Event> specification =
                Specification.where(EventSpecification.hasStatus(EventStatus.DRAFT))
                .and(EventSpecification.hasCategory(category))
                .and(EventSpecification.titleContains(keyword))
                .and(EventSpecification.startsAfter(from))
                .and(EventSpecification.startBefore(to));
        return eventRepository.findAll(specification, pageable);
    }

    @Override
    public void delete(UUID eventId) {
        Event event = getOrThrow(eventId);
        event.setDeleteAt(Instant.now());
        eventRepository.save(event);
    }

    public Event getOrThrow(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }
}

