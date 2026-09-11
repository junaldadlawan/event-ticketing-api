package com.junaldadlawan.event_ticketing_api.tickettemplate.dto;

import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link TicketTemplate}, matching openapi.yaml's {@code
 * TicketTemplate} schema (flattened branding fields, same convention as
 * every other response DTO in this codebase - see {@code
 * TicketTypeResponse}/{@code PromoCodeResponse}).
 */
public record TicketTemplateResponse(
        UUID id,
        UUID eventId,
        UUID ticketTypeId,
        TicketTemplateFormat format,
        String logoUrl,
        String backgroundImageUrl,
        String primaryColor,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) implements Serializable {

    public static TicketTemplateResponse from(TicketTemplate ticketTemplate) {
        return new TicketTemplateResponse(
                ticketTemplate.getId(),
                ticketTemplate.getEventId(),
                ticketTemplate.getTicketTypeId(),
                ticketTemplate.getFormat(),
                ticketTemplate.getLogoUrl(),
                ticketTemplate.getBackgroundImageUrl(),
                ticketTemplate.getPrimaryColor(),
                ticketTemplate.getCreatedBy(),
                ticketTemplate.getCreatedAt(),
                ticketTemplate.getUpdatedBy(),
                ticketTemplate.getUpdatedAt());
    }
}
