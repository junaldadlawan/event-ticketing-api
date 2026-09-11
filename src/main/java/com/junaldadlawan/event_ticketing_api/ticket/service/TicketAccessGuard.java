package com.junaldadlawan.event_ticketing_api.ticket.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Shared ticket-visibility check (BR-CART-004-equivalent): the owning buyer,
 * the event's organizer/owner (resolved via {@code ticket.eventId -> Event ->
 * organizationId}), or an admin. Factored out of {@code TicketServiceImpl}
 * (Phase 6a) so {@code TicketArtifactServiceImpl} (Phase 6b) can reuse the
 * exact same rule rather than duplicating it.
 */
@Component
@RequiredArgsConstructor
public class TicketAccessGuard {

    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;

    public void requireOwnerBuyerOrOrganizerOrAdmin(Ticket ticket) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        if (ticket.getOwnerId().equals(callerId)) {
            return;
        }
        Event event = eventRepository.findByIdAndDeletedAtIsNull(ticket.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event " + ticket.getEventId() + " not found"));
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, event.getOrganizationId(), OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, event.getOrganizationId(), OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the ticket's owning buyer, the event's organizer/owner, or an admin may view this ticket");
        }
    }
}
