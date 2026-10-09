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
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.BackgroundFit;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import com.junaldadlawan.event_ticketing_api.tickettemplate.repository.TicketTemplateRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.upload.service.ImageStorageService;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Renders a ticket's deliverable artifact fresh on every call (Phase 6b
 * confirmed decision #1 - no {@code TicketArtifact} persistence at all).
 */
@Service
@RequiredArgsConstructor
public class TicketArtifactServiceImpl implements TicketArtifactService {

    private static final String UPLOADS_PATH = "/api/v1/uploads/files/";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final String EM_DASH = "—";

    private final TicketRepository ticketRepository;
    private final TicketAccessGuard ticketAccessGuard;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final SeatRepository seatRepository;
    private final TicketTemplateRepository ticketTemplateRepository;
    private final VenueRepository venueRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;
    private final QrCodeGenerator qrCodeGenerator;
    private final BarcodeGenerator barcodeGenerator;
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

        Seat seat = resolveSeat(ticket);
        String seatDescription = describeSeat(seat);

        // This is the one place in the codebase allowed to read the raw
        // credential for a purpose other than generating it (Phase 6a's
        // TicketResponse deliberately never exposes it) - it is only ever
        // fed into the QR encoder below, never logged or included in any
        // response/error message.
        // Deliberately the 1-arg overload (default size) - see
        // QrCodeGeneratorTest's javadoc: requesting an arbitrary non-default
        // size from ZXing's own encoder was found to decode unreliably, so
        // renderers rescale this fixed-size image themselves instead.
        TicketTemplate template = resolveTemplate(ticket.getEventId(), ticket.getTicketTypeId(), format);
        CodePlacement codePlacement = codePlacementOf(template);
        boolean printCode = template == null || template.getCodeType() != CodeType.NONE;
        // A template may ask for a barcode instead of the QR code (both carry the same credential), or for no code.
        BufferedImage codeImage;
        if (!printCode) {
            codeImage = null;
        } else if (codePlacement != null && codePlacement.type() == CodeType.BARCODE) {
            codeImage = barcodeGenerator.generate(ticket.getCredential());
        } else {
            codeImage = qrCodeGenerator.generate(ticket.getCredential());
        }

        TicketDesign design = null;
        TicketValues values = null;
        if (usesDesigner(template)) {
            design = designOf(template, printCode);
            values = valuesOf(ticket, event, ticketType, seat);
        }

        TicketArtifactFields fields = new TicketArtifactFields(
                event.getTitle(),
                ticketType.getName(),
                seatDescription,
                ticket.getTicketNumber(),
                codeImage,
                template != null ? template.getPrimaryColor() : null,
                codePlacement,
                design,
                values);

        String filenameBase = "ticket-" + ticket.getTicketNumber();
        return switch (format) {
            case DIGITAL -> new RenderedTicketArtifact(pngTicketRenderer.render(fields), "image/png", filenameBase + ".png");
            case PHYSICAL -> new RenderedTicketArtifact(pdfTicketRenderer.render(fields), "application/pdf", filenameBase + ".pdf");
        };
    }

    private static CodePlacement codePlacementOf(TicketTemplate template) {
        if (template == null || template.getCodeType() == null || template.getCodeType() == CodeType.NONE
                || template.getCodeX() == null || template.getCodeY() == null || template.getCodeWidth() == null) {
            return null;
        }
        int rotation = template.getCodeRotation() == null ? 0 : template.getCodeRotation();
        return new CodePlacement(template.getCodeType(), template.getCodeX(), template.getCodeY(),
                template.getCodeWidth(), rotation);
    }

    /** The ticket's seat, or {@code null} for general admission. */
    private Seat resolveSeat(Ticket ticket) {
        if (ticket.getSeatId() == null) {
            return null;
        }
        return seatRepository.findById(ticket.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat " + ticket.getSeatId() + " not found"));
    }

    private static String describeSeat(Seat seat) {
        if (seat == null) {
            return "General Admission";
        }
        return "Section " + seat.getSection() + ", Row " + seat.getRow() + ", Seat " + seat.getSeatNumber();
    }

    /** Any designer content on the template switches the ticket from the built-in layout to the designed one. */
    private static boolean usesDesigner(TicketTemplate template) {
        return template != null
                && (template.getTicketWidth() != null || template.getTicketHeight() != null
                || template.getBackgroundColor() != null || template.getBackgroundImageUrl() != null
                || !template.getTextFields().isEmpty() || template.getCodeType() == CodeType.NONE);
    }

    private TicketDesign designOf(TicketTemplate template, boolean printCode) {
        TicketDesign.Rect rect = template.getBackgroundFit() == BackgroundFit.CUSTOM
                ? new TicketDesign.Rect(template.getBackgroundX(), template.getBackgroundY(),
                        template.getBackgroundWidth(), template.getBackgroundHeight())
                : null;
        return new TicketDesign(
                template.getTicketWidth() == null ? TicketDesign.DEFAULT_WIDTH : template.getTicketWidth(),
                template.getTicketHeight() == null ? TicketDesign.DEFAULT_HEIGHT : template.getTicketHeight(),
                template.getBackgroundColor(),
                loadBackgroundImage(template.getBackgroundImageUrl()),
                template.getBackgroundFit(),
                rect,
                List.copyOf(template.getTextFields()),
                printCode);
    }

    /**
     * The background image, read from our own upload folder - never fetched over the network. Only a URL
     * pointing at {@code /api/v1/uploads/files/<name>} is honoured; anything else, a missing file or a
     * format Java cannot decode (WebP) means the ticket is drawn without it rather than failing.
     */
    private BufferedImage loadBackgroundImage(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        int marker = url.indexOf(UPLOADS_PATH);
        if (marker < 0) {
            return null;
        }
        String name = url.substring(marker + UPLOADS_PATH.length());
        int end = name.indexOf('?');
        if (end >= 0) {
            name = name.substring(0, end);
        }
        end = name.indexOf('#');
        if (end >= 0) {
            name = name.substring(0, end);
        }
        try (InputStream in = imageStorageService.load(name).resource().getInputStream()) {
            return ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * What each field key prints for this ticket. The attendee is the ticket's CURRENT owner (so it follows
     * transfers); date and time are the event's, in the event's own time zone.
     */
    private TicketValues valuesOf(Ticket ticket, Event event, TicketType ticketType, Seat seat) {
        ZonedDateTime start = event.getStartAt().atZone(zoneOf(event.getTimezone()));
        String venueName = event.getVenueId() == null ? "" : venueRepository.findById(event.getVenueId())
                .map(Venue::getName).orElse("");
        String attendee = userRepository.findById(ticket.getOwnerId()).map(User::getName).orElse("");
        return new TicketValues(
                ticketType.getName(),
                seat == null ? "GA" : seat.getSection(),
                seat == null ? EM_DASH : seat.getRow(),
                seat == null ? EM_DASH : seat.getSeatNumber(),
                ticket.getTicketNumber(),
                attendee,
                event.getTitle(),
                start.format(DATE_FORMAT),
                start.format(TIME_FORMAT),
                venueName);
    }

    private static ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException e) {
            return ZoneId.of("UTC");
        }
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
