package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketAccessGuard;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import com.junaldadlawan.event_ticketing_api.tickettemplate.repository.TicketTemplateRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link TicketArtifactServiceImpl} — closes the
 * code-reviewer MEDIUM finding: zero prior coverage for {@code
 * ticket/artifact/}. Uses the REAL {@link QrCodeGenerator} (a stateless
 * component with no dependencies of its own) alongside mocked repositories/
 * renderers, so the {@link BufferedImage} captured off the real render call
 * can be genuinely ZXing-decoded — proving what's actually encoded rather
 * than merely that a QR-shaped image was produced.
 * <p>
 * Covers the two behaviors the dispatch flagged as security-load-bearing
 * (BR-TICKET-001/008): the QR must encode the ticket's raw {@code
 * credential} (never {@code ticketNumber}/{@code id}), and template
 * resolution must prefer a ticket-type-specific template over an
 * event-level one.
 */
@ExtendWith(MockitoExtension.class)
class TicketArtifactServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketAccessGuard ticketAccessGuard;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository ticketTypeRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private TicketTemplateRepository ticketTemplateRepository;

    @Mock
    private PngTicketRenderer pngTicketRenderer;

    @Mock
    private PdfTicketRenderer pdfTicketRenderer;

    /** The real thing — no dependencies to mock, and we need genuine QR bytes to decode. */
    private final QrCodeGenerator qrCodeGenerator = new QrCodeGenerator();

    private TicketArtifactServiceImpl service;

    private UUID eventId;
    private UUID ticketTypeId;

    @BeforeEach
    void setUp() {
        service = new TicketArtifactServiceImpl(
                ticketRepository, ticketAccessGuard, eventRepository, ticketTypeRepository,
                seatRepository, ticketTemplateRepository, qrCodeGenerator, pngTicketRenderer, pdfTicketRenderer);
        eventId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
    }

    private Ticket ticket(UUID id, UUID seatId, String credential) {
        return Ticket.builder()
                .id(id)
                .orderId(UUID.randomUUID())
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .seatId(seatId)
                .ownerId(UUID.randomUUID())
                .ticketNumber("ABC-A1B2C3")
                .credential(credential)
                .status(TicketStatus.VALID)
                .build();
    }

    private Event event() {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return Event.builder()
                .id(eventId)
                .organizationId(UUID.randomUUID())
                .title("Concert Night")
                .description("desc")
                .category("music")
                .ticketPrefix("ABC")
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
    }

    private TicketType ticketType() {
        Instant saleStartAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(5, ChronoUnit.DAYS);
        return TicketType.builder()
                .id(ticketTypeId)
                .eventId(eventId)
                .name("General Admission")
                .kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .quantityTotal(100)
                .quantityAvailable(100)
                .saleStartAt(saleStartAt)
                .saleEndAt(saleEndAt)
                .maxPerOrder(10)
                .build();
    }

    private Seat seat(UUID id) {
        return Seat.builder()
                .id(id)
                .seatMapId(UUID.randomUUID())
                .section("A")
                .row("3")
                .seatNumber("12")
                .status(SeatStatus.SOLD)
                .build();
    }

    private TicketTemplate template(UUID ticketTypeIdOrNull, String color) {
        return TicketTemplate.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .ticketTypeId(ticketTypeIdOrNull)
                .format(TicketTemplateFormat.DIGITAL)
                .primaryColor(color)
                .build();
    }

    private String decode(BufferedImage image) throws Exception {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        return new MultiFormatReader().decode(bitmap).getText();
    }

    private void mockGaTicketFoundation(Ticket ticket) {
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event()));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType()));
    }

    // ---- visibility delegation ----

    @Test
    void render_ticketNotFound_throwsResourceNotFound() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.render(ticketId, TicketTemplateFormat.DIGITAL))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ticketAccessGuard);
    }

    @Test
    void render_accessDenied_propagatesForbidden_withoutRenderingAnything() {
        Ticket ticket = ticket(UUID.randomUUID(), null, "cred-1");
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        org.mockito.Mockito.doThrow(new ForbiddenException("nope"))
                .when(ticketAccessGuard).requireOwnerBuyerOrOrganizerOrAdmin(ticket);

        assertThatThrownBy(() -> service.render(ticket.getId(), TicketTemplateFormat.DIGITAL))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(pngTicketRenderer);
    }

    @Test
    void render_eventNoLongerExists_throwsResourceNotFound() {
        Ticket ticket = ticket(UUID.randomUUID(), null, "cred-1");
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.render(ticket.getId(), TicketTemplateFormat.DIGITAL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void render_ticketTypeNoLongerExists_throwsResourceNotFound() {
        Ticket ticket = ticket(UUID.randomUUID(), null, "cred-1");
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event()));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.render(ticket.getId(), TicketTemplateFormat.DIGITAL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- seat description resolution ----

    @Test
    void render_gaTicket_seatDescriptionIsGeneralAdmission_withoutTouchingSeatRepository() throws Exception {
        Ticket ticket = ticket(UUID.randomUUID(), null, "cred-ga");
        mockGaTicketFoundation(ticket);
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdAndFormatAndDeletedAtIsNull(eventId, ticketTypeId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.empty());
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdIsNullAndFormatAndDeletedAtIsNull(eventId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.empty());
        when(pngTicketRenderer.render(org.mockito.ArgumentMatchers.any())).thenReturn(new byte[]{1});

        ArgumentCaptor<TicketArtifactFields> captor = ArgumentCaptor.forClass(TicketArtifactFields.class);
        service.render(ticket.getId(), TicketTemplateFormat.DIGITAL);
        verify(pngTicketRenderer).render(captor.capture());

        assertThat(captor.getValue().seatDescription()).isEqualTo("General Admission");
        verifyNoInteractions(seatRepository);
    }

    @Test
    void render_reservedSeatTicket_seatDescriptionIncludesSectionRowSeatNumber() throws Exception {
        UUID seatId = UUID.randomUUID();
        Ticket ticket = ticket(UUID.randomUUID(), seatId, "cred-seat");
        mockGaTicketFoundation(ticket);
        when(seatRepository.findById(seatId)).thenReturn(Optional.of(seat(seatId)));
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdAndFormatAndDeletedAtIsNull(eventId, ticketTypeId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.empty());
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdIsNullAndFormatAndDeletedAtIsNull(eventId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.empty());
        when(pngTicketRenderer.render(org.mockito.ArgumentMatchers.any())).thenReturn(new byte[]{1});

        ArgumentCaptor<TicketArtifactFields> captor = ArgumentCaptor.forClass(TicketArtifactFields.class);
        service.render(ticket.getId(), TicketTemplateFormat.DIGITAL);
        verify(pngTicketRenderer).render(captor.capture());

        assertThat(captor.getValue().seatDescription()).isEqualTo("Section A, Row 3, Seat 12");
    }

    @Test
    void render_reservedSeatTicket_seatNoLongerExists_throwsResourceNotFound() {
        UUID seatId = UUID.randomUUID();
        Ticket ticket = ticket(UUID.randomUUID(), seatId, "cred-seat");
        mockGaTicketFoundation(ticket);
        when(seatRepository.findById(seatId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.render(ticket.getId(), TicketTemplateFormat.DIGITAL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- THE key security-relevant test: QR encodes the raw credential, never ticketNumber/id ----

    @Test
    void render_qrCode_encodesActualCredential_neverTicketNumberOrId() throws Exception {
        String realCredential = UUID.randomUUID() + "." + "reallySecretSignatureXYZ";
        Ticket ticket = ticket(UUID.randomUUID(), null, realCredential);
        mockGaTicketFoundation(ticket);
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdAndFormatAndDeletedAtIsNull(eventId, ticketTypeId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.empty());
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdIsNullAndFormatAndDeletedAtIsNull(eventId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.empty());
        when(pngTicketRenderer.render(org.mockito.ArgumentMatchers.any())).thenReturn(new byte[]{1});

        ArgumentCaptor<TicketArtifactFields> captor = ArgumentCaptor.forClass(TicketArtifactFields.class);
        service.render(ticket.getId(), TicketTemplateFormat.DIGITAL);
        verify(pngTicketRenderer).render(captor.capture());

        String decoded = decode(captor.getValue().qrCodeImage());

        assertThat(decoded).isEqualTo(realCredential);
        assertThat(decoded).isNotEqualTo(ticket.getTicketNumber());
        assertThat(decoded).isNotEqualTo(ticket.getId().toString());
    }

    // ---- template resolution order (the other security-relevant behavior) ----

    @Test
    void render_templateResolution_ticketTypeSpecificPreferredOverEventLevel() throws Exception {
        Ticket ticket = ticket(UUID.randomUUID(), null, "cred-1");
        mockGaTicketFoundation(ticket);
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdAndFormatAndDeletedAtIsNull(eventId, ticketTypeId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.of(template(ticketTypeId, "#111111")));
        when(pngTicketRenderer.render(org.mockito.ArgumentMatchers.any())).thenReturn(new byte[]{1});

        ArgumentCaptor<TicketArtifactFields> captor = ArgumentCaptor.forClass(TicketArtifactFields.class);
        service.render(ticket.getId(), TicketTemplateFormat.DIGITAL);
        verify(pngTicketRenderer).render(captor.capture());

        assertThat(captor.getValue().primaryColorHex()).isEqualTo("#111111");
        // The event-level fallback lookup must never even run once the
        // ticket-type-specific lookup already matched (Optional#or short-circuits).
        verify(ticketTemplateRepository, never())
                .findByEventIdAndTicketTypeIdIsNullAndFormatAndDeletedAtIsNull(eventId, TicketTemplateFormat.DIGITAL);
    }

    @Test
    void render_templateResolution_eventLevelFallback_whenNoTicketTypeSpecificTemplate() throws Exception {
        Ticket ticket = ticket(UUID.randomUUID(), null, "cred-1");
        mockGaTicketFoundation(ticket);
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdAndFormatAndDeletedAtIsNull(eventId, ticketTypeId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.empty());
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdIsNullAndFormatAndDeletedAtIsNull(eventId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.of(template(null, "#222222")));
        when(pngTicketRenderer.render(org.mockito.ArgumentMatchers.any())).thenReturn(new byte[]{1});

        ArgumentCaptor<TicketArtifactFields> captor = ArgumentCaptor.forClass(TicketArtifactFields.class);
        service.render(ticket.getId(), TicketTemplateFormat.DIGITAL);
        verify(pngTicketRenderer).render(captor.capture());

        assertThat(captor.getValue().primaryColorHex()).isEqualTo("#222222");
    }

    @Test
    void render_templateResolution_noTemplateAtAll_fieldsPrimaryColorIsNull_stillRenders() throws Exception {
        Ticket ticket = ticket(UUID.randomUUID(), null, "cred-1");
        mockGaTicketFoundation(ticket);
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdAndFormatAndDeletedAtIsNull(eventId, ticketTypeId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.empty());
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdIsNullAndFormatAndDeletedAtIsNull(eventId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.empty());
        when(pngTicketRenderer.render(org.mockito.ArgumentMatchers.any())).thenReturn(new byte[]{1});

        ArgumentCaptor<TicketArtifactFields> captor = ArgumentCaptor.forClass(TicketArtifactFields.class);
        RenderedTicketArtifact result = service.render(ticket.getId(), TicketTemplateFormat.DIGITAL);
        verify(pngTicketRenderer).render(captor.capture());

        assertThat(captor.getValue().primaryColorHex()).isNull();
        assertThat(result.contentType()).isEqualTo("image/png");
    }

    // ---- format switch: content type / filename ----

    @Test
    void render_digitalFormat_returnsPngContentTypeAndFilename() {
        Ticket ticket = ticket(UUID.randomUUID(), null, "cred-1");
        mockGaTicketFoundation(ticket);
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdAndFormatAndDeletedAtIsNull(eventId, ticketTypeId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.empty());
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdIsNullAndFormatAndDeletedAtIsNull(eventId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.empty());
        when(pngTicketRenderer.render(org.mockito.ArgumentMatchers.any())).thenReturn(new byte[]{9, 9});

        RenderedTicketArtifact result = service.render(ticket.getId(), TicketTemplateFormat.DIGITAL);

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.filename()).isEqualTo("ticket-ABC-A1B2C3.png");
        assertThat(result.content()).isEqualTo(new byte[]{9, 9});
        verifyNoInteractions(pdfTicketRenderer);
    }

    @Test
    void render_physicalFormat_returnsPdfContentTypeAndFilename() {
        Ticket ticket = ticket(UUID.randomUUID(), null, "cred-1");
        mockGaTicketFoundation(ticket);
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdAndFormatAndDeletedAtIsNull(eventId, ticketTypeId, TicketTemplateFormat.PHYSICAL))
                .thenReturn(Optional.empty());
        when(ticketTemplateRepository.findByEventIdAndTicketTypeIdIsNullAndFormatAndDeletedAtIsNull(eventId, TicketTemplateFormat.PHYSICAL))
                .thenReturn(Optional.empty());
        when(pdfTicketRenderer.render(org.mockito.ArgumentMatchers.any())).thenReturn(new byte[]{7, 7});

        RenderedTicketArtifact result = service.render(ticket.getId(), TicketTemplateFormat.PHYSICAL);

        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.filename()).isEqualTo("ticket-ABC-A1B2C3.pdf");
        assertThat(result.content()).isEqualTo(new byte[]{7, 7});
        verifyNoInteractions(pngTicketRenderer);
    }
}
