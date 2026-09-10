package com.junaldadlawan.event_ticketing_api.tickettype.controller;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeResponse;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.service.TicketTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ticket-types")
@RequiredArgsConstructor
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    @GetMapping("/{ticketTypeId}")
    public TicketTypeResponse get(@PathVariable UUID ticketTypeId) {
        return TicketTypeResponse.from(ticketTypeService.get(ticketTypeId));
    }

    @PatchMapping("/{ticketTypeId}")
    public TicketTypeResponse update(@PathVariable UUID ticketTypeId, @Valid @RequestBody TicketTypeUpdateRequest request) {
        return TicketTypeResponse.from(ticketTypeService.update(ticketTypeId, request));
    }
}
