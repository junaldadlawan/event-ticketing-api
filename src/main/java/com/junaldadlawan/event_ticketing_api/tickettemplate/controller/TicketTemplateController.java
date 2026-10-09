package com.junaldadlawan.event_ticketing_api.tickettemplate.controller;

import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateResponse;
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettemplate.service.TicketTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * All ticket-template endpoints: create/list are addressed through the event,
 * update and delete by the template's own id. openapi.yaml defines no GET-single
 * for {@code TicketTemplate}.
 */
@RestController
@RequiredArgsConstructor
public class TicketTemplateController {

    private final TicketTemplateService ticketTemplateService;

    @PostMapping("/api/v1/events/{eventId}/ticket-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTemplateResponse create(@PathVariable UUID eventId, @Valid @RequestBody TicketTemplateCreateRequest request) {
        return TicketTemplateResponse.from(ticketTemplateService.create(eventId, request));
    }

    @GetMapping("/api/v1/events/{eventId}/ticket-templates")
    public List<TicketTemplateResponse> list(@PathVariable UUID eventId) {
        return ticketTemplateService.list(eventId).stream().map(TicketTemplateResponse::from).toList();
    }

    @PatchMapping("/api/v1/ticket-templates/{templateId}")
    public TicketTemplateResponse update(@PathVariable UUID templateId, @Valid @RequestBody TicketTemplateUpdateRequest request) {
        return TicketTemplateResponse.from(ticketTemplateService.update(templateId, request));
    }

    @DeleteMapping("/api/v1/ticket-templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID templateId) {
        ticketTemplateService.delete(templateId);
    }
}
