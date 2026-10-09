package com.junaldadlawan.event_ticketing_api.tickettemplate.dto;

import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.BackgroundFit;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
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
        CodeType codeType,
        Double codeX,
        Double codeY,
        Double codeWidth,
        Integer codeRotation,
        Integer ticketWidth,
        Integer ticketHeight,
        String backgroundColor,
        BackgroundFit backgroundFit,
        Double backgroundX,
        Double backgroundY,
        Double backgroundWidth,
        Double backgroundHeight,
        List<TicketTextFieldDto> textFields,
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
                ticketTemplate.getCodeType(),
                ticketTemplate.getCodeX(),
                ticketTemplate.getCodeY(),
                ticketTemplate.getCodeWidth(),
                ticketTemplate.getCodeRotation(),
                ticketTemplate.getTicketWidth(),
                ticketTemplate.getTicketHeight(),
                ticketTemplate.getBackgroundColor(),
                ticketTemplate.getBackgroundFit(),
                ticketTemplate.getBackgroundX(),
                ticketTemplate.getBackgroundY(),
                ticketTemplate.getBackgroundWidth(),
                ticketTemplate.getBackgroundHeight(),
                ticketTemplate.getTextFields().stream().map(TicketTextFieldDto::from).toList(),
                ticketTemplate.getCreatedBy(),
                ticketTemplate.getCreatedAt(),
                ticketTemplate.getUpdatedBy(),
                ticketTemplate.getUpdatedAt());
    }
}
