package com.junaldadlawan.event_ticketing_api.tickettype.controller;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeOrderRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeResponse;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeSalesStatusRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeSuspensionRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.service.TicketTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * All ticket-type endpoints: create/list are addressed through the event,
 * get/update by the ticket type's own id.
 */
@RestController
@RequiredArgsConstructor
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    @PostMapping("/api/v1/events/{eventId}/ticket-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse create(@PathVariable UUID eventId, @Valid @RequestBody TicketTypeCreateRequest request) {
        return TicketTypeResponse.from(ticketTypeService.create(eventId, request));
    }

    @GetMapping("/api/v1/events/{eventId}/ticket-types")
    public List<TicketTypeResponse> list(@PathVariable UUID eventId) {
        List<TicketType> ticketTypes = ticketTypeService.list(eventId);
        boolean manager = ticketTypeService.mayManage(eventId);
        return ticketTypes.stream().map(TicketTypeResponse::from).map(response -> manager ? response : response.withoutReason()).toList();
    }

    /** Saves the arrangement of the event's ticket types (the organizer drags them into order). */
    @PutMapping("/api/v1/events/{eventId}/ticket-types/order")
    public List<TicketTypeResponse> reorder(@PathVariable UUID eventId, @Valid @RequestBody TicketTypeOrderRequest request) {
        return ticketTypeService.reorder(eventId, request.ticketTypeIds()).stream().map(TicketTypeResponse::from).toList();
    }

    @GetMapping("/api/v1/ticket-types/{ticketTypeId}")
    public TicketTypeResponse get(@PathVariable UUID ticketTypeId) {
        TicketType ticketType = ticketTypeService.get(ticketTypeId);
        TicketTypeResponse response = TicketTypeResponse.from(ticketType);
        return ticketTypeService.mayManage(ticketType.getEventId()) ? response : response.withoutReason();
    }

    @PatchMapping("/api/v1/ticket-types/{ticketTypeId}")
    public TicketTypeResponse update(@PathVariable UUID ticketTypeId, @Valid @RequestBody TicketTypeUpdateRequest request) {
        return TicketTypeResponse.from(ticketTypeService.update(ticketTypeId, request));
    }

    /** Pause or resume selling this ticket type: the one place that sets its sales state. */
    @PutMapping("/api/v1/ticket-types/{ticketTypeId}/sales-status")
    public TicketTypeResponse setSalesStatus(@PathVariable UUID ticketTypeId,
                                             @Valid @RequestBody TicketTypeSalesStatusRequest request) {
        return TicketTypeResponse.from(ticketTypeService.setSalesStatus(ticketTypeId, request.status()));
    }

    /** Admin only: suspend selling this ticket type, with a reason its organizers see. */
    @PostMapping("/api/v1/ticket-types/{ticketTypeId}/suspension")
    public TicketTypeResponse suspend(@PathVariable UUID ticketTypeId, @Valid @RequestBody TicketTypeSuspensionRequest request) {
        return TicketTypeResponse.from(ticketTypeService.suspend(ticketTypeId, request.reason()));
    }

    /** Admin only: lift the suspension. */
    @DeleteMapping("/api/v1/ticket-types/{ticketTypeId}/suspension")
    public TicketTypeResponse unsuspend(@PathVariable UUID ticketTypeId) {
        return TicketTypeResponse.from(ticketTypeService.unsuspend(ticketTypeId));
    }

    @DeleteMapping("/api/v1/ticket-types/{ticketTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID ticketTypeId) {
        ticketTypeService.delete(ticketTypeId);
    }
}
