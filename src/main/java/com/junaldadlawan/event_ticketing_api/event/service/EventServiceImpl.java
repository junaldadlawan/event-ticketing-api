package com.junaldadlawan.event_ticketing_api.event.service;


import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.dto.EventRequest;
import com.junaldadlawan.event_ticketing_api.event.dto.EventUpdateRequest;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.event.specification.EventSpecification;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private static final String TICKET_PREFIX_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    // 26^3 = 17,576 possible 3-letter codes; this cap should never realistically be hit.
    private static final int MAX_TICKET_PREFIX_ATTEMPTS = 50;
    private static final Set<EventStatus> CANCELLABLE_STATUSES =
            EnumSet.of(EventStatus.DRAFT, EventStatus.PUBLISHED, EventStatus.ON_SALE, EventStatus.SOLD_OUT);

    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final VenueRepository venueRepository;
    private final OrganizationAccessGuard accessGuard;
    private final SecureRandom random = new SecureRandom();

    @Override
    public Event createEvent(EventRequest request) {
        Organization organization = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization " + request.organizationId() + " not found"));
        if (organization.getStatus() != OrganizationStatus.APPROVED) {
            throw new ForbiddenException("Organization is not approved");
        }

        // BR-AUTH-004: admins have platform-wide access, unscoped by organization —
        // consistent with update/publish/cancel/delete's admin bypass below.
        if (!accessGuard.isAdmin()) {
            UUID callerId = accessGuard.currentUserId();
            if (!isOwnerOrOrganizer(callerId, organization.getId())) {
                throw new ForbiddenException("Only the organization's owner or organizer may create events for it");
            }
        }

        if (request.venueId() != null) {
            Venue venue = venueRepository.findById(request.venueId())
                    .orElseThrow(() -> new ResourceNotFoundException("Venue " + request.venueId() + " not found"));
            if (!venue.getOrganizationId().equals(organization.getId())) {
                throw new BadRequestException("Venue does not belong to the specified organization");
            }
        }

        Event newEvent = Event.builder()
                .organizationId(organization.getId())
                .title(request.title())
                .description(request.description())
                .category(request.category())
                .venueId(request.venueId())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .timezone(request.timezone())
                .images(request.images() == null ? new ArrayList<>() : new ArrayList<>(request.images()))
                .status(EventStatus.DRAFT)
                .ticketPrefix(generateUniqueTicketPrefix())
                .build();
        return eventRepository.save(newEvent);
    }

    @Override
    public Page<Event> listEvents(String category, String keyword, Instant from, Instant to, Pageable pageable) {
        Specification<Event> specification =
                Specification.where(EventSpecification.hasStatus(EventStatus.PUBLISHED))
                .and(EventSpecification.notDeleted())
                .and(EventSpecification.hasCategory(category))
                .and(EventSpecification.titleContains(keyword))
                .and(EventSpecification.startsAfter(from))
                .and(EventSpecification.startBefore(to));
        return eventRepository.findAll(specification, pageable);
    }

    @Override
    public Event getEvent(UUID eventId) {
        Event event = getOrThrow(eventId);
        if (event.getStatus() == EventStatus.DRAFT) {
            requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        }
        return event;
    }

    @Override
    public Event updateEvent(UUID eventId, EventUpdateRequest request) {
        Event event = getOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BadRequestException("Event title must not be blank");
            }
            event.setTitle(request.title());
        }
        if (request.description() != null) {
            if (request.description().isBlank()) {
                throw new BadRequestException("Event description must not be blank");
            }
            event.setDescription(request.description());
        }
        if (request.category() != null) {
            if (request.category().isBlank()) {
                throw new BadRequestException("Event category must not be blank");
            }
            event.setCategory(request.category());
        }
        if (request.images() != null) {
            event.setImages(new ArrayList<>(request.images()));
        }

        return eventRepository.save(event);
    }

    @Override
    public Event publishEvent(UUID eventId) {
        Event event = getOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        Organization organization = organizationRepository.findById(event.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization " + event.getOrganizationId() + " not found"));
        if (organization.getStatus() != OrganizationStatus.APPROVED) {
            throw new ConflictException("Organization is not approved");
        }
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ConflictException("Event is not in a publishable state");
        }

        event.setStatus(EventStatus.PUBLISHED);
        return eventRepository.save(event);
    }

    @Override
    public Event cancelEvent(UUID eventId) {
        Event event = getOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        if (!CANCELLABLE_STATUSES.contains(event.getStatus())) {
            throw new ConflictException("Event is not in a cancellable state");
        }

        event.setStatus(EventStatus.CANCELLED);
        // NOTE: no refund logic here — the async refund workflow for issued
        // tickets is Phase 8's job (Phase 8 doesn't exist yet); this only
        // performs the status transition itself, per the roadmap's own note.
        return eventRepository.save(event);
    }

    @Override
    public void delete(UUID eventId) {
        Event event = getOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        event.markDeleted();
        eventRepository.save(event);
    }

    @Override
    public Venue resolveVenue(UUID venueId) {
        if (venueId == null) {
            return null;
        }
        return venueRepository.findById(venueId).orElse(null);
    }

    public Event getOrThrow(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    /**
     * Reused for every event-mutation check plus draft-visibility on
     * {@code getEvent}: admin bypasses entirely; otherwise the caller must
     * be authenticated (an anonymous caller is rejected with
     * {@link ForbiddenException} here, not an NPE) and hold OWNER/ORGANIZER
     * on the event's own organization — always read from the persisted
     * entity, never from client input.
     */
    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        if (!isOwnerOrOrganizer(callerId, organizationId)) {
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event");
        }
    }

    private boolean isOwnerOrOrganizer(UUID userId, UUID organizationId) {
        return accessGuard.hasRole(userId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(userId, organizationId, OrganizationRole.ORGANIZER);
    }

    private String generateUniqueTicketPrefix() {
        for (int attempt = 0; attempt < MAX_TICKET_PREFIX_ATTEMPTS; attempt++) {
            StringBuilder candidate = new StringBuilder(3);
            for (int i = 0; i < 3; i++) {
                candidate.append(TICKET_PREFIX_ALPHABET.charAt(random.nextInt(TICKET_PREFIX_ALPHABET.length())));
            }
            String prefix = candidate.toString();
            if (!eventRepository.existsByTicketPrefix(prefix)) {
                return prefix;
            }
        }
        throw new IllegalStateException(
                "Unable to generate a unique ticket prefix after " + MAX_TICKET_PREFIX_ATTEMPTS + " attempts");
    }
}
