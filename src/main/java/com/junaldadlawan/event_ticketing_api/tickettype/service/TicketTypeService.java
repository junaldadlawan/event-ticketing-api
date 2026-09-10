package com.junaldadlawan.event_ticketing_api.tickettype.service;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;

import java.util.List;
import java.util.UUID;

public interface TicketTypeService {

    TicketType create(UUID eventId, TicketTypeCreateRequest request);

    List<TicketType> list(UUID eventId);

    TicketType get(UUID ticketTypeId);

    TicketType update(UUID ticketTypeId, TicketTypeUpdateRequest request);
}
