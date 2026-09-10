package com.junaldadlawan.event_ticketing_api.tickettype.controller;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeResponse;
import com.junaldadlawan.event_ticketing_api.tickettype.service.TicketTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/ticket-types")
@RequiredArgsConstructor
public class EventTicketTypeController {

    private final TicketTypeService ticketTypeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse create(@PathVariable UUID eventId, @Valid @RequestBody TicketTypeCreateRequest request) {
        return TicketTypeResponse.from(ticketTypeService.create(eventId, request));
    }

    @GetMapping
    public List<TicketTypeResponse> list(@PathVariable UUID eventId) {
        return ticketTypeService.list(eventId).stream().map(TicketTypeResponse::from).toList();
    }
}
