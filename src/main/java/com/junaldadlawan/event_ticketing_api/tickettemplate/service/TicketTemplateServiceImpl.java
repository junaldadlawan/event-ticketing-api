package com.junaldadlawan.event_ticketing_api.tickettemplate.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;
import com.junaldadlawan.event_ticketing_api.tickettemplate.repository.TicketTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketTemplateServiceImpl implements TicketTemplateService {

    private final TicketTemplateRepository ticketTemplateRepository;
    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;

    @Override
    public TicketTemplate create(UUID eventId, TicketTemplateCreateRequest request) {
        Event event = getEventOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        TicketTemplate ticketTemplate = TicketTemplate.builder()
                .eventId(event.getId())
                .ticketTypeId(request.ticketTypeId())
                .format(request.format())
                .logoUrl(request.logoUrl())
                .backgroundImageUrl(request.backgroundImageUrl())
                .primaryColor(request.primaryColor())
                .build();
        return ticketTemplateRepository.save(ticketTemplate);
    }

    @Override
    public List<TicketTemplate> list(UUID eventId) {
        Event event = getEventOrThrow(eventId);
        // Owning organizer/admin only, always - openapi.yaml's
        // listTicketTemplates summary says "owning organizer" with no
        // public/draft-based visibility split, same shape as PromoCode's
        // list (Phase 5a), unlike TicketType's DRAFT-only gate.
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        return ticketTemplateRepository.findByEventIdAndDeletedAtIsNull(eventId);
    }

    @Override
    public TicketTemplate update(UUID templateId, TicketTemplateUpdateRequest request) {
        TicketTemplate ticketTemplate = getOrThrow(templateId);
        // Owning organizationId is always resolved from the persisted
        // template's own event, never from client input (non-IDOR pattern
        // established in Phases 2-3 onward).
        Event event = getEventOrThrow(ticketTemplate.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        if (request.logoUrl() != null) {
            ticketTemplate.setLogoUrl(request.logoUrl());
        }
        if (request.backgroundImageUrl() != null) {
            ticketTemplate.setBackgroundImageUrl(request.backgroundImageUrl());
        }
        if (request.primaryColor() != null) {
            ticketTemplate.setPrimaryColor(request.primaryColor());
        }

        return ticketTemplateRepository.save(ticketTemplate);
    }

    private TicketTemplate getOrThrow(UUID templateId) {
        return ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket template " + templateId + " not found"));
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    /**
     * Mirrors {@code EventServiceImpl}/{@code TicketTypeServiceImpl}/{@code
     * PromoCodeServiceImpl}'s copy of the same BR-AUTH-004 admin-bypass shape.
     */
    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's ticket templates");
        }
    }
}
