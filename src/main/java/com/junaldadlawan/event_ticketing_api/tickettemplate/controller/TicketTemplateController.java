package com.junaldadlawan.event_ticketing_api.tickettemplate.controller;

import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateResponse;
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettemplate.service.TicketTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * openapi.yaml defines no GET-single and no DELETE for {@code
 * TicketTemplate} - only {@code PATCH /ticket-templates/{templateId}} at
 * this top-level prefix (list/create live under {@code
 * /events/{eventId}/ticket-templates}, see {@link EventTicketTemplateController}).
 */
@RestController
@RequestMapping("/api/v1/ticket-templates")
@RequiredArgsConstructor
public class TicketTemplateController {

    private final TicketTemplateService ticketTemplateService;

    @PatchMapping("/{templateId}")
    public TicketTemplateResponse update(@PathVariable UUID templateId, @Valid @RequestBody TicketTemplateUpdateRequest request) {
        return TicketTemplateResponse.from(ticketTemplateService.update(templateId, request));
    }
}
