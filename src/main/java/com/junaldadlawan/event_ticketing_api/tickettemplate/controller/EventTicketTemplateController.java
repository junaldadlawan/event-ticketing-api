package com.junaldadlawan.event_ticketing_api.tickettemplate.controller;

import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateResponse;
import com.junaldadlawan.event_ticketing_api.tickettemplate.service.TicketTemplateService;
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
@RequestMapping("/api/v1/events/{eventId}/ticket-templates")
@RequiredArgsConstructor
public class EventTicketTemplateController {

    private final TicketTemplateService ticketTemplateService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTemplateResponse create(@PathVariable UUID eventId, @Valid @RequestBody TicketTemplateCreateRequest request) {
        return TicketTemplateResponse.from(ticketTemplateService.create(eventId, request));
    }

    @GetMapping
    public List<TicketTemplateResponse> list(@PathVariable UUID eventId) {
        return ticketTemplateService.list(eventId).stream().map(TicketTemplateResponse::from).toList();
    }
}
