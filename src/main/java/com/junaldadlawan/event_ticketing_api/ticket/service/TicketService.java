package com.junaldadlawan.event_ticketing_api.ticket.service;

import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;

import java.util.UUID;

public interface TicketService {

    /**
     * {@code GET /tickets/{ticketId}} — visible to the owning buyer ({@code
     * ticket.ownerId == caller}), the event's organizer/owner, or an admin.
     */
    Ticket getTicket(UUID ticketId);
}
