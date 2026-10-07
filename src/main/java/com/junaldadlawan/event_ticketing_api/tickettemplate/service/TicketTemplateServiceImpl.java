package com.junaldadlawan.event_ticketing_api.tickettemplate.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTextFieldDto;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTextField;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.BackgroundFit;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextFieldKey;
import com.junaldadlawan.event_ticketing_api.tickettemplate.repository.TicketTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketTemplateServiceImpl implements TicketTemplateService {

    private static final int MAX_CUSTOM_TEXT_FIELDS = 10;
    /** Ticket width in px when the template doesn't set one (same as the renderer's default). */
    private static final int DEFAULT_TICKET_WIDTH_PX = 900;

    private final TicketTemplateRepository ticketTemplateRepository;
    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;

    @Override
    public TicketTemplate create(UUID eventId, TicketTemplateCreateRequest request) {
        Event event = getEventOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        TicketTemplate ticketTemplate = TicketTemplate.builder()
                .eventId(event.getId())
                .ticketTypeId(request.ticketTypeId())
                .format(request.format())
                .logoUrl(request.logoUrl())
                .backgroundImageUrl(blankToNull(request.backgroundImageUrl()))
                .primaryColor(request.primaryColor())
                .codeType(request.codeType())
                .codeX(request.codeX())
                .codeY(request.codeY())
                .codeWidth(request.codeWidth())
                .codeRotation(request.codeRotation())
                .ticketWidth(request.ticketWidth())
                .ticketHeight(request.ticketHeight())
                .backgroundColor(blankToNull(request.backgroundColor()))
                .backgroundFit(request.backgroundFit())
                .backgroundX(request.backgroundX())
                .backgroundY(request.backgroundY())
                .backgroundWidth(request.backgroundWidth())
                .backgroundHeight(request.backgroundHeight())
                .textFields(request.textFields() == null ? new ArrayList<>() : toTextFields(request.textFields()))
                .build();
        requireValidCodePlacement(ticketTemplate);
        requireValidBackground(ticketTemplate);
        return ticketTemplateRepository.save(ticketTemplate);
    }

    @Override
    public List<TicketTemplate> list(UUID eventId) {
        Event event = getEventOrThrow(eventId);
        // Owning organizer/admin only, always - openapi.yaml's
        // listTicketTemplates summary says "owning organizer" with no
        // public/draft-based visibility split, same shape as PromoCode's
        // list (Phase 5a), unlike TicketType's DRAFT-only gate.
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        return ticketTemplateRepository.findByEventIdAndDeletedAtIsNull(eventId);
    }

    @Override
    public TicketTemplate update(UUID templateId, TicketTemplateUpdateRequest request) {
        TicketTemplate ticketTemplate = getOrThrow(templateId);
        // Owning organizationId is always resolved from the persisted
        // template's own event, never from client input (non-IDOR pattern
        // established in Phases 2-3 onward).
        Event event = getEventOrThrow(ticketTemplate.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        // A PATCH can't send null to mean "clear", so switching to NONE / to a non-CUSTOM fit clears the
        // values that no longer apply - but a request that names such a value alongside is contradictory.
        if (request.codeType() == CodeType.NONE && (request.codeX() != null || request.codeY() != null
                || request.codeWidth() != null || request.codeRotation() != null)) {
            throw new BadRequestException("codeType NONE prints no code, so codeX, codeY, codeWidth and codeRotation must not be given");
        }
        if (request.backgroundFit() != null && request.backgroundFit() != BackgroundFit.CUSTOM
                && (request.backgroundX() != null || request.backgroundY() != null
                || request.backgroundWidth() != null || request.backgroundHeight() != null)) {
            throw new BadRequestException("backgroundX, backgroundY, backgroundWidth and backgroundHeight are only for backgroundFit CUSTOM");
        }

        if (request.logoUrl() != null) {
            ticketTemplate.setLogoUrl(request.logoUrl());
        }
        if (request.backgroundImageUrl() != null) {
            // "" clears the background (the designer sends it when the image is removed).
            ticketTemplate.setBackgroundImageUrl(blankToNull(request.backgroundImageUrl()));
        }
        if (request.primaryColor() != null) {
            ticketTemplate.setPrimaryColor(request.primaryColor());
        }
        if (request.codeType() != null) {
            ticketTemplate.setCodeType(request.codeType());
            if (request.codeType() == CodeType.NONE) {
                ticketTemplate.setCodeX(null);
                ticketTemplate.setCodeY(null);
                ticketTemplate.setCodeWidth(null);
                ticketTemplate.setCodeRotation(null);
            }
        }
        if (request.codeX() != null) {
            ticketTemplate.setCodeX(request.codeX());
        }
        if (request.codeY() != null) {
            ticketTemplate.setCodeY(request.codeY());
        }
        if (request.codeWidth() != null) {
            ticketTemplate.setCodeWidth(request.codeWidth());
        }
        if (request.codeRotation() != null) {
            ticketTemplate.setCodeRotation(request.codeRotation());
        }

        if (request.ticketWidth() != null) {
            ticketTemplate.setTicketWidth(request.ticketWidth());
        }
        if (request.ticketHeight() != null) {
            ticketTemplate.setTicketHeight(request.ticketHeight());
        }
        if (request.backgroundColor() != null) {
            // "" clears it (white).
            ticketTemplate.setBackgroundColor(blankToNull(request.backgroundColor()));
        }
        if (request.backgroundFit() != null) {
            ticketTemplate.setBackgroundFit(request.backgroundFit());
            if (request.backgroundFit() != BackgroundFit.CUSTOM) {
                ticketTemplate.setBackgroundX(null);
                ticketTemplate.setBackgroundY(null);
                ticketTemplate.setBackgroundWidth(null);
                ticketTemplate.setBackgroundHeight(null);
            }
        }
        if (request.backgroundX() != null) {
            ticketTemplate.setBackgroundX(request.backgroundX());
        }
        if (request.backgroundY() != null) {
            ticketTemplate.setBackgroundY(request.backgroundY());
        }
        if (request.backgroundWidth() != null) {
            ticketTemplate.setBackgroundWidth(request.backgroundWidth());
        }
        if (request.backgroundHeight() != null) {
            ticketTemplate.setBackgroundHeight(request.backgroundHeight());
        }
        if (request.textFields() != null) {
            // The whole list is replaced (it is ordered; [] = no text fields).
            List<TicketTextField> replacement = toTextFields(request.textFields());
            ticketTemplate.getTextFields().clear();
            ticketTemplate.getTextFields().addAll(replacement);
        }
        // Validated on the merged result, so a PATCH of one value can't leave a half-set or invalid design.
        requireValidCodePlacement(ticketTemplate);
        requireValidBackground(ticketTemplate);

        return ticketTemplateRepository.save(ticketTemplate);
    }

    @Override
    public void delete(UUID templateId) {
        TicketTemplate ticketTemplate = getOrThrow(templateId);
        // Same rule as update: the owning organization comes from the template's own event, never from client input.
        Event event = getEventOrThrow(ticketTemplate.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        ticketTemplate.markDeleted();
        ticketTemplateRepository.save(ticketTemplate);
        BusinessAuditLogger.record("ticket_template.deleted", "TicketTemplate", templateId, BusinessAuditLogger.Outcome.SUCCESS);
    }

    /**
     * The code placement is all-or-nothing (type, x, y, width; rotation defaults to 0), the
     * width must reach the type's scannable minimum, the box must fit horizontally. Any type may be
     * rotated. Vertical fit can't be checked here (the ticket's height isn't known
     * until render time), so the renderers keep the (rotated) code on the ticket themselves.
     * {@code NONE} prints no code and so carries no placement values at all.
     */
    private void requireValidCodePlacement(TicketTemplate template) {
        CodeType type = template.getCodeType();
        Double x = template.getCodeX();
        Double y = template.getCodeY();
        Double width = template.getCodeWidth();
        if (type == CodeType.NONE) {
            if (x != null || y != null || width != null
                    || (template.getCodeRotation() != null && template.getCodeRotation() != 0)) {
                throw new BadRequestException("codeType NONE prints no code, so codeX, codeY, codeWidth and codeRotation must not be given");
            }
            template.setCodeRotation(null);
            return;
        }
        int given = (type != null ? 1 : 0) + (x != null ? 1 : 0) + (y != null ? 1 : 0) + (width != null ? 1 : 0);
        if (given == 0) {
            if (template.getCodeRotation() != null && template.getCodeRotation() != 0) {
                throw new BadRequestException("codeRotation needs a code placement (codeType, codeX, codeY, codeWidth)");
            }
            return;
        }
        if (given != 4) {
            throw new BadRequestException("codeType, codeX, codeY and codeWidth must be given together");
        }
        int ticketWidthPx = template.getTicketWidth() != null ? template.getTicketWidth() : DEFAULT_TICKET_WIDTH_PX;
        double minWidth = type.minWidthPercent(ticketWidthPx);
        // Tolerance: the client derives the same % from the inch minimum and may round it.
        if (width < minWidth - 0.01) {
            String minimum = type.minWidthInches() > 0
                    ? type.minWidthInches() + " in (" + String.format(java.util.Locale.ROOT, "%.2f", minWidth) + "% of this ticket's width)"
                    : minWidth + "% of the ticket width";
            throw new BadRequestException("A " + type + " must be at least " + minimum
                    + " to stay scannable (codeWidth was " + width + ")");
        }
        if (x + width > 100.0) {
            throw new BadRequestException("The code must fit inside the ticket: codeX + codeWidth must not exceed 100");
        }
        if (template.getCodeRotation() == null) {
            template.setCodeRotation(0);
        }
    }

    /** CUSTOM fit needs the whole rectangle; any other fit (including none = COVER) must not have one. */
    private void requireValidBackground(TicketTemplate template) {
        boolean anyRect = template.getBackgroundX() != null || template.getBackgroundY() != null
                || template.getBackgroundWidth() != null || template.getBackgroundHeight() != null;
        boolean fullRect = template.getBackgroundX() != null && template.getBackgroundY() != null
                && template.getBackgroundWidth() != null && template.getBackgroundHeight() != null;
        if (template.getBackgroundFit() == BackgroundFit.CUSTOM) {
            if (!fullRect) {
                throw new BadRequestException("backgroundFit CUSTOM needs backgroundX, backgroundY, backgroundWidth and backgroundHeight");
            }
        } else if (anyRect) {
            throw new BadRequestException("backgroundX, backgroundY, backgroundWidth and backgroundHeight are only for backgroundFit CUSTOM");
        }
    }

    /**
     * Checks the rules that depend on a field's key and turns the requested fields into stored ones
     * (trimmed text, default rotation 0 / not bold). List order is kept: it is the drawing order.
     * <ul>
     *   <li>every key except CUSTOM at most once; CUSTOM at most {@value #MAX_CUSTOM_TEXT_FIELDS} times;</li>
     *   <li>CUSTOM needs {@code text}, which no other key may have; {@code sampleLength}/{@code sampleText}
     *       are for dynamic keys only; {@code lineBreaks} for static keys and CUSTOM only;</li>
     *   <li>line breaks strictly ascending, and (CUSTOM) each before the end of its text.</li>
     * </ul>
     */
    private List<TicketTextField> toTextFields(List<TicketTextFieldDto> requested) {
        Set<TextFieldKey> seen = EnumSet.noneOf(TextFieldKey.class);
        int custom = 0;
        List<TicketTextField> fields = new ArrayList<>();
        for (TicketTextFieldDto dto : requested) {
            TextFieldKey key = dto.key();
            if (key == TextFieldKey.CUSTOM) {
                custom++;
                if (custom > MAX_CUSTOM_TEXT_FIELDS) {
                    throw new BadRequestException("At most " + MAX_CUSTOM_TEXT_FIELDS + " CUSTOM text fields are allowed");
                }
            } else if (!seen.add(key)) {
                throw new BadRequestException("Text field " + key + " may only appear once");
            }

            String text = trimToNull(dto.text());
            String sampleText = trimToNull(dto.sampleText());
            if (dto.text() != null && text == null) {
                throw new BadRequestException("Text field " + key + ": text must not be blank");
            }
            if (dto.sampleText() != null && sampleText == null) {
                throw new BadRequestException("Text field " + key + ": sampleText must not be blank");
            }
            if (key == TextFieldKey.CUSTOM && text == null) {
                throw new BadRequestException("A CUSTOM text field needs text");
            }
            if (key != TextFieldKey.CUSTOM && text != null) {
                throw new BadRequestException("Text field " + key + " cannot have text; only CUSTOM prints its own text");
            }
            if (!key.isDynamic() && (sampleText != null || dto.sampleLength() != null)) {
                throw new BadRequestException("Text field " + key + " cannot have sampleText or sampleLength; they are for per-ticket fields only");
            }
            List<Integer> lineBreaks = dto.lineBreaks() == null ? List.of() : dto.lineBreaks();
            if (key.isDynamic() && !lineBreaks.isEmpty()) {
                throw new BadRequestException("Text field " + key + " cannot have lineBreaks; it is always one line");
            }
            int previous = 0;
            for (int position : lineBreaks) {
                if (position <= previous) {
                    throw new BadRequestException("lineBreaks must be unique and ascending positions greater than 0");
                }
                if (key == TextFieldKey.CUSTOM && position >= text.length()) {
                    throw new BadRequestException("Each lineBreak of a CUSTOM text field must be before the end of its text (" + text.length() + " characters)");
                }
                previous = position;
            }

            fields.add(TicketTextField.builder()
                    .key(key)
                    .x(dto.x())
                    .y(dto.y())
                    .fontSize(dto.fontSize())
                    .color(dto.color())
                    .bold(Boolean.TRUE.equals(dto.bold()))
                    .align(dto.align())
                    .rotation(dto.rotation() == null ? 0 : dto.rotation())
                    .sampleLength(dto.sampleLength())
                    .sampleText(sampleText)
                    .text(text)
                    .lineBreaks(lineBreaks.isEmpty() ? null : new ArrayList<>(lineBreaks))
                    .build());
        }
        return fields;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TicketTemplate getOrThrow(UUID templateId) {
        return ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket template " + templateId + " not found"));
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    /**
     * Mirrors {@code EventServiceImpl}/{@code TicketTypeServiceImpl}/{@code
     * PromoCodeServiceImpl}'s copy of the same BR-AUTH-004 admin-bypass shape.
     */
    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's ticket templates");
        }
    }
}
