package com.junaldadlawan.event_ticketing_api.seatmap.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatMapRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatMapServiceImpl implements SeatMapService {

    private final SeatMapRepository seatMapRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;

    @Override
    public SeatMap getSeatMap(UUID eventId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
        if (event.getStatus() == EventStatus.DRAFT) {
            requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        }
        return seatMapRepository.findByEventIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event has no seat map"));
    }

    @Override
    public List<Seat> getSeats(UUID seatMapId) {
        return seatRepository.findBySeatMapId(seatMapId);
    }

    /**
     * Mirrors {@code EventServiceImpl.requireOwnerOrOrganizerOrAdmin} /
     * {@code TicketTypeServiceImpl}'s copy of the same BR-AUTH-004
     * admin-bypass shape.
     */
    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may view this event's seat map");
        }
    }
}
