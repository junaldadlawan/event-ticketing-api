package com.junaldadlawan.event_ticketing_api.ticket.service;

import com.junaldadlawan.event_ticketing_api.ticket.dto.MyTicketEventResponse;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TicketService {

    /**
     * {@code GET /tickets/{ticketId}} — visible to the owning buyer ({@code
     * ticket.ownerId == caller}), the event's organizer/owner, or an admin.
     */
    Ticket getTicket(UUID ticketId);

    /** {@code GET /users/me/tickets}: the caller's own tickets, whatever order they came from; empty {@code statuses} = all, null {@code eventId} = every event. */
    Page<Ticket> listMyTickets(Set<TicketStatus> statuses, UUID eventId, Pageable pageable);

    /** {@code GET /users/me/tickets/events}: the events the caller holds tickets for now, soonest first. */
    List<MyTicketEventResponse> listMyTicketEvents();
}
