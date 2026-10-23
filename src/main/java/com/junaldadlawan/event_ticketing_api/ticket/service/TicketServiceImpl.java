package com.junaldadlawan.event_ticketing_api.ticket.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.ticket.dto.MyTicketEventResponse;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketAccessGuard ticketAccessGuard;
    private final OrganizationAccessGuard accessGuard;

    @Override
    public Ticket getTicket(UUID ticketId) {
        Ticket ticket = getOrThrow(ticketId);
        ticketAccessGuard.requireOwnerBuyerOrOrganizerOrAdmin(ticket);
        return ticket;
    }

    @Override
    public Page<Ticket> listMyTickets(Set<TicketStatus> statuses, UUID eventId, Pageable pageable) {
        return ticketRepository.findHeldBy(accessGuard.currentUserId(),
                statuses == null || statuses.isEmpty() ? EnumSet.allOf(TicketStatus.class) : statuses, eventId, pageable);
    }

    @Override
    public List<MyTicketEventResponse> listMyTicketEvents() {
        return ticketRepository.findHeldEvents(accessGuard.currentUserId());
    }

    public Ticket getOrThrow(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + ticketId + " not found"));
    }
}
