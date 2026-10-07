package com.junaldadlawan.event_ticketing_api.tickettype.service;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.SalesStatus;

import java.util.List;
import java.util.UUID;

public interface TicketTypeService {

    TicketType create(UUID eventId, TicketTypeCreateRequest request);

    List<TicketType> list(UUID eventId);

    TicketType get(UUID ticketTypeId);

    TicketType update(UUID ticketTypeId, TicketTypeUpdateRequest request);

    /**
     * Saves the organizer's arrangement of the event's ticket types (owning organizer / admin): the given ids, first to
     * last, become positions 0..n-1. The list must contain every ticket type of the event exactly once.
     * Returns the event's ticket types in the new order.
     */
    List<TicketType> reorder(UUID eventId, List<UUID> orderedTicketTypeIds);

    /** Soft-deletes a ticket type that has no sales or holds (owning organizer / admin). */
    void delete(UUID ticketTypeId);

    /**
     * Puts the ticket type in the given sales state (owning organizer / admin). {@code PAUSED}: it can no longer
     * be added to a cart and a cart already holding it can no longer check out; tickets already sold and items
     * already held are untouched. {@code ACTIVE}: normal selling again. Asking for the state it is already in
     * changes nothing.
     */
    TicketType setSalesStatus(UUID ticketTypeId, SalesStatus status);
}
