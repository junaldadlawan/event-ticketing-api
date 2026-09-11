package com.junaldadlawan.event_ticketing_api.tickettemplate.service;

import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;

import java.util.List;
import java.util.UUID;

public interface TicketTemplateService {

    TicketTemplate create(UUID eventId, TicketTemplateCreateRequest request);

    List<TicketTemplate> list(UUID eventId);

    TicketTemplate update(UUID templateId, TicketTemplateUpdateRequest request);
}
