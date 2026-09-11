package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketAccessGuard;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import com.junaldadlawan.event_ticketing_api.tickettemplate.repository.TicketTemplateRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.UUID;

/**
 * Renders a ticket's deliverable artifact fresh on every call (Phase 6b
 * confirmed decision #1 - no {@code TicketArtifact} persistence at all).
 */
@Service
@RequiredArgsConstructor
public class TicketArtifactServiceImpl implements TicketArtifactService {

    private final TicketRepository ticketRepository;
    private final TicketAccessGuard ticketAccessGuard;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final SeatRepository seatRepository;
    private final TicketTemplateRepository ticketTemplateRepository;
    private final QrCodeGenerator qrCodeGenerator;
    private final PngTicketRenderer pngTicketRenderer;
    private final PdfTicketRenderer pdfTicketRenderer;

    @Override
    public RenderedTicketArtifact render(UUID ticketId, TicketTemplateFormat format) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + ticketId + " not found"));
        // Same BR-CART-004-equivalent visibility rule as GET /tickets/{id}
        // (Phase 6a), reused rather than duplicated.
        ticketAccessGuard.requireOwnerBuyerOrOrganizerOrAdmin(ticket);

        Event event = eventRepository.findByIdAndDeletedAtIsNull(ticket.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event " + ticket.getEventId() + " not found"));
        TicketType ticketType = ticketTypeRepository.findByIdAndDeletedAtIsNull(ticket.getTicketTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type " + ticket.getTicketTypeId() + " not found"));

        String seatDescription = resolveSeatDescription(ticket);

        // This is the one place in the codebase allowed to read the raw
        // credential for a purpose other than generating it (Phase 6a's
        // TicketResponse deliberately never exposes it) - it is only ever
        // fed into the QR encoder below, never logged or included in any
        // response/error message.
        // Deliberately the 1-arg overload (default size) - see
        // QrCodeGeneratorTest's javadoc: requesting an arbitrary non-default
        // size from ZXing's own encoder was found to decode unreliably, so
        // renderers rescale this fixed-size image themselves instead.
        BufferedImage qrCodeImage = qrCodeGenerator.generate(ticket.getCredential());

        TicketTemplate template = resolveTemplate(ticket.getEventId(), ticket.getTicketTypeId(), format);

        TicketArtifactFields fields = new TicketArtifactFields(
                event.getTitle(),
                ticketType.getName(),
                seatDescription,
                ticket.getTicketNumber(),
                qrCodeImage,
                template != null ? template.getPrimaryColor() : null);

        String filenameBase = "ticket-" + ticket.getTicketNumber();
        return switch (format) {
            case DIGITAL -> new RenderedTicketArtifact(pngTicketRenderer.render(fields), "image/png", filenameBase + ".png");
            case PHYSICAL -> new RenderedTicketArtifact(pdfTicketRenderer.render(fields), "application/pdf", filenameBase + ".pdf");
        };
    }

    private String resolveSeatDescription(Ticket ticket) {
        if (ticket.getSeatId() == null) {
            return "General Admission";
        }
        Seat seat = seatRepository.findById(ticket.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat " + ticket.getSeatId() + " not found"));
        return "Section " + seat.getSection() + ", Row " + seat.getRow() + ", Seat " + seat.getSeatNumber();
    }

    /**
     * Ticket-type-specific template first, then the event-level fallback
     * (null {@code ticketTypeId}), then {@code null} - meaning "no
     * organizer-configured template", which the caller renders as the
     * built-in default (no branding) rather than failing the request
     * (Phase 6b confirmed decision #5).
     */
    private TicketTemplate resolveTemplate(UUID eventId, UUID ticketTypeId, TicketTemplateFormat format) {
        return ticketTemplateRepository
                .findByEventIdAndTicketTypeIdAndFormatAndDeletedAtIsNull(eventId, ticketTypeId, format)
                .or(() -> ticketTemplateRepository.findByEventIdAndTicketTypeIdIsNullAndFormatAndDeletedAtIsNull(eventId, format))
                .orElse(null);
    }
}
