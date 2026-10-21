package com.junaldadlawan.event_ticketing_api.ticket.controller;

import com.junaldadlawan.event_ticketing_api.checkin.service.TicketCheckInLookup;
import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.ticket.dto.MyTicketEventResponse;
import com.junaldadlawan.event_ticketing_api.ticket.dto.TicketCheckInResponse;
import com.junaldadlawan.event_ticketing_api.ticket.dto.TicketResponse;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** {@code GET /users/me/tickets} and {@code /users/me/tickets/events} - kept apart from {@link TicketController}, whose paths all sit under /tickets. */
@RestController
@RequiredArgsConstructor
public class MyTicketsController {

    private final TicketService ticketService;
    private final TicketCheckInLookup ticketCheckInLookup;

    /** Every ticket the caller holds now (bought, transferred to them, or bought on resale); soonest event first by default. */
    @GetMapping("/api/v1/users/me/tickets")
    public PageResponse<TicketResponse> listMyTickets(@RequestParam(required = false) Set<TicketStatus> status,
                                                      @RequestParam(required = false) UUID eventId,
                                                      @PageableDefault(size = 20, sort = "e.startAt") Pageable pageable) {
        Page<Ticket> page = ticketService.listMyTickets(status, eventId, pageable);
        Map<UUID, TicketCheckInResponse> checkIns = ticketCheckInLookup.forTickets(page.getContent());
        return PageResponse.from(page.map(t -> TicketResponse.from(t, checkIns.get(t.getId()))));
    }

    /** The events the caller holds tickets for now - fills My tickets' event filter. Not paged. */
    @GetMapping("/api/v1/users/me/tickets/events")
    public List<MyTicketEventResponse> listMyTicketEvents() {
        return ticketService.listMyTicketEvents();
    }
}
