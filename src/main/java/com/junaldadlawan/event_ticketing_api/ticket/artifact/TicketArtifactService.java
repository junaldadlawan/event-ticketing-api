package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;

import java.util.UUID;

public interface TicketArtifactService {

    /**
     * {@code GET /tickets/{ticketId}/artifact} — re-renders the ticket fresh
     * from {@code Ticket}+{@code TicketTemplate}(+{@code Event}/{@code
     * TicketType}/{@code Seat}) data on every call (Phase 6b confirmed
     * decision #1: no persistence). Visibility follows the exact same rule
     * as {@code GET /tickets/{ticketId}} (owning buyer, event organizer/
     * owner, or admin).
     */
    RenderedTicketArtifact render(UUID ticketId, TicketTemplateFormat format);
}
