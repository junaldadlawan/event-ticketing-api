package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
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
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTextField;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.BackgroundFit;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextAlign;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextFieldKey;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import com.junaldadlawan.event_ticketing_api.tickettemplate.repository.TicketTemplateRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.upload.service.ImageStorageService;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What {@link TicketArtifactServiceImpl} hands the renderers when a template uses the ticket designer:
 * which values each field key gets for THIS ticket, when the designed layout is used at all, and how the
 * background image is (only ever) loaded.
 */
@ExtendWith(MockitoExtension.class)
class TicketArtifactDesignValuesTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketAccessGuard ticketAccessGuard;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private TicketTemplateRepository ticketTemplateRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private PngTicketRenderer pngTicketRenderer;
    @Mock
    private PdfTicketRenderer pdfTicketRenderer;

    private TicketArtifactServiceImpl service;

    private UUID eventId;
    private UUID ticketTypeId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        service = new TicketArtifactServiceImpl(ticketRepository, ticketAccessGuard, eventRepository, ticketTypeRepository,
                seatRepository, ticketTemplateRepository, venueRepository, userRepository, imageStorageService,
                new QrCodeGenerator(), new BarcodeGenerator(), pngTicketRenderer, pdfTicketRenderer);
        eventId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        lenient().when(pngTicketRenderer.render(any())).thenReturn(new byte[] {1});
    }

    // ---- fixtures ----

    private Ticket ticket(UUID seatId) {
        return Ticket.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID()).eventId(eventId).ticketTypeId(ticketTypeId)
                .seatId(seatId).ownerId(ownerId).ticketNumber("ABC-A1B2C3")
                .credential("11111111-2222-3333-4444-555555555555:1.signatureValue-0123456789abcdefghij")
                .status(TicketStatus.VALID).build();
    }

    private Event event(String timezone, UUID venueId, Instant startAt) {
        return Event.builder().id(eventId).organizationId(UUID.randomUUID()).title("Concert Night").description("d")
                .category("Music").ticketPrefix("ABC").venueId(venueId).startAt(startAt).endAt(startAt.plusSeconds(7200))
                .timezone(timezone).build();
    }

    private TicketType ticketType() {
        return TicketType.builder().id(ticketTypeId).eventId(eventId).name("VIP").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build()).quantityTotal(10).quantityAvailable(10)
                .saleStartAt(Instant.now()).saleEndAt(Instant.now().plusSeconds(3600)).maxPerOrder(5).build();
    }

    private Seat seat(UUID id) {
        return Seat.builder().id(id).seatMapId(UUID.randomUUID()).section("B").row("7").seatNumber("21")
                .status(SeatStatus.SOLD).build();
    }

    private TicketTemplate.TicketTemplateBuilder template() {
        return TicketTemplate.builder().id(UUID.randomUUID()).eventId(eventId).format(TicketTemplateFormat.DIGITAL);
    }

    private TicketTextField anyField() {
        return TicketTextField.builder().key(TextFieldKey.EVENT_NAME).x(10).y(50).fontSize(8).color("#000000")
                .align(TextAlign.LEFT).build();
    }

    private TicketArtifactFields render(Ticket ticket, Event event, TicketTemplate template) {
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType()));
        lenient().when(ticketTemplateRepository.findByEventIdAndTicketTypeIdAndFormatAndDeletedAtIsNull(eventId, ticketTypeId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.ofNullable(template));
        lenient().when(ticketTemplateRepository.findByEventIdAndTicketTypeIdIsNullAndFormatAndDeletedAtIsNull(eventId, TicketTemplateFormat.DIGITAL))
                .thenReturn(Optional.empty());
        service.render(ticket.getId(), TicketTemplateFormat.DIGITAL);
        ArgumentCaptor<TicketArtifactFields> captor = ArgumentCaptor.forClass(TicketArtifactFields.class);
        verify(pngTicketRenderer).render(captor.capture());
        return captor.getValue();
    }

    private void owner(String name) {
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(User.builder().id(ownerId).firstName(name.split(" ", 2)[0]).lastName(name.contains(" ") ? name.split(" ", 2)[1] : "").build()));
    }

    private byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    // ---- when the designed layout is used ----

    @Test
    void aTemplateWithOnlyBranding_keepsTheBuiltInLayout() {
        TicketArtifactFields fields = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().primaryColor("#112233").build());

        assertThat(fields.design()).isNull();
        assertThat(fields.values()).isNull();
        assertThat(fields.codeImage()).isNotNull();
    }

    @Test
    void noTemplateAtAll_keepsTheBuiltInLayout() {
        TicketArtifactFields fields = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")), null);

        assertThat(fields.design()).isNull();
    }

    @Test
    void anyDesignerContent_switchesToTheDesignedLayout() throws Exception {
        for (TicketTemplate designed : List.of(
                template().ticketWidth(800).build(),
                template().ticketHeight(300).build(),
                template().backgroundColor("#FFFFFF").build(),
                template().backgroundImageUrl("https://example.com/x.png").build(),
                template().textFields(new java.util.ArrayList<>(List.of(anyField()))).build(),
                template().codeType(CodeType.NONE).build())) {
            org.mockito.Mockito.reset(pngTicketRenderer, ticketRepository, eventRepository, ticketTypeRepository, ticketTemplateRepository);
            lenient().when(pngTicketRenderer.render(any())).thenReturn(new byte[] {1});

            TicketArtifactFields fields = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")), designed);

            assertThat(fields.design()).as("template %s", designed).isNotNull();
            assertThat(fields.values()).isNotNull();
        }
    }

    @Test
    void theCanvasDefaultsTo900By380_andUsesTheDesignedSizeWhenSet() {
        TicketArtifactFields defaults = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().backgroundColor("#FFFFFF").build());
        assertThat(defaults.design().width()).isEqualTo(900);
        assertThat(defaults.design().height()).isEqualTo(380);

        org.mockito.Mockito.reset(pngTicketRenderer, ticketRepository, eventRepository, ticketTypeRepository, ticketTemplateRepository);
        lenient().when(pngTicketRenderer.render(any())).thenReturn(new byte[] {1});
        TicketArtifactFields sized = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().ticketWidth(1200).ticketHeight(500).build());
        assertThat(sized.design().width()).isEqualTo(1200);
        assertThat(sized.design().height()).isEqualTo(500);
    }

    // ---- the values each key prints ----

    @Test
    void aSeatedTicket_printsItsSectionRowAndSeat() {
        UUID seatId = UUID.randomUUID();
        when(seatRepository.findById(seatId)).thenReturn(Optional.of(seat(seatId)));
        owner("Jamie Cruz");

        TicketValues values = render(ticket(seatId), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().ticketWidth(800).build()).values();

        assertThat(values.section()).isEqualTo("B");
        assertThat(values.row()).isEqualTo("7");
        assertThat(values.seat()).isEqualTo("21");
        assertThat(values.ticketType()).isEqualTo("VIP");
        assertThat(values.ticketNumber()).isEqualTo("ABC-A1B2C3");
        assertThat(values.eventName()).isEqualTo("Concert Night");
    }

    @Test
    void generalAdmission_printsGaAndDashes() {
        owner("Jamie Cruz");

        TicketValues values = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().ticketWidth(800).build()).values();

        assertThat(values.section()).isEqualTo("GA");
        assertThat(values.row()).isEqualTo("—");
        assertThat(values.seat()).isEqualTo("—");
    }

    @Test
    void theAttendeeIsTheCurrentOwner_soATransferChangesIt() {
        Ticket transferred = ticket(null);
        UUID newOwner = UUID.randomUUID();
        transferred.setOwnerId(newOwner);
        when(userRepository.findById(newOwner)).thenReturn(Optional.of(User.builder().id(newOwner).firstName("Riley").lastName("Park").build()));

        TicketValues values = render(transferred, event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().ticketWidth(800).build()).values();

        assertThat(values.attendeeName()).isEqualTo("Riley Park");
    }

    @Test
    void aMissingOwner_printsAnEmptyAttendee() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        TicketValues values = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().ticketWidth(800).build()).values();

        assertThat(values.attendeeName()).isEmpty();
    }

    @Test
    void theEventDateAndTime_areInTheEventsOwnTimeZone() {
        owner("Jamie Cruz");
        // 01:30 UTC on 24 Dec is 8:30 PM on Wed 23 Dec in New York (UTC-5)
        TicketValues newYork = render(ticket(null), event("America/New_York", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().ticketWidth(800).build()).values();
        assertThat(newYork.eventDate()).isEqualTo("Wed, Dec 23, 2026");
        assertThat(newYork.eventTime()).isEqualTo("8:30 PM");

        org.mockito.Mockito.reset(pngTicketRenderer, ticketRepository, eventRepository, ticketTypeRepository, ticketTemplateRepository);
        lenient().when(pngTicketRenderer.render(any())).thenReturn(new byte[] {1});
        // the same instant is 9:30 AM on Thu 24 Dec in Manila (UTC+8)
        TicketValues manila = render(ticket(null), event("Asia/Manila", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().ticketWidth(800).build()).values();
        assertThat(manila.eventDate()).isEqualTo("Thu, Dec 24, 2026");
        assertThat(manila.eventTime()).isEqualTo("9:30 AM");
    }

    @Test
    void midnightAndNoonUseTheTwelveHourClock() {
        owner("Jamie Cruz");

        TicketValues values = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T00:05:00Z")),
                template().ticketWidth(800).build()).values();

        assertThat(values.eventTime()).isEqualTo("12:05 AM");
    }

    @Test
    void theVenueIsItsName_orNothingWhenTheEventHasNone() {
        UUID venueId = UUID.randomUUID();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(
                Venue.builder().id(venueId).organizationId(UUID.randomUUID()).name("Main Hall").build()));
        owner("Jamie Cruz");

        assertThat(render(ticket(null), event("UTC", venueId, Instant.parse("2026-12-24T01:30:00Z")),
                template().ticketWidth(800).build()).values().venue()).isEqualTo("Main Hall");

        org.mockito.Mockito.reset(pngTicketRenderer, ticketRepository, eventRepository, ticketTypeRepository, ticketTemplateRepository);
        lenient().when(pngTicketRenderer.render(any())).thenReturn(new byte[] {1});
        assertThat(render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().ticketWidth(800).build()).values().venue()).isEmpty();
    }

    // ---- the code ----

    @Test
    void codeTypeNone_printsNoCode() {
        TicketArtifactFields fields = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().codeType(CodeType.NONE).build());

        assertThat(fields.codeImage()).isNull();
        assertThat(fields.codePlacement()).isNull();
        assertThat(fields.design().printCode()).isFalse();
    }

    @Test
    void aBarcodeTemplate_inTheDesignedLayout_getsABarcodeWithThePlacement() {
        TicketArtifactFields fields = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().ticketWidth(900).codeType(CodeType.BARCODE).codeX(5.0).codeY(20.0).codeWidth(45.0).codeRotation(0).build());

        assertThat(fields.codeImage().getWidth()).isCloseTo(fields.codeImage().getHeight() * 3, org.assertj.core.api.Assertions.within(2));
        assertThat(fields.codePlacement()).isEqualTo(new CodePlacement(CodeType.BARCODE, 5.0, 20.0, 45.0, 0));
        assertThat(fields.design().printCode()).isTrue();
    }

    // ---- background image: only ever our own files ----

    @Test
    void aBackgroundImageOnOurUploadsPath_isLoadedFromDisk() throws Exception {
        when(imageStorageService.load("0b1f1b94-3a54-4b5e-9d2f-1f2b3c4d5e6f.png"))
                .thenReturn(new ImageStorageService.LoadedImage(new ByteArrayResource(png()), "image/png"));
        owner("Jamie Cruz");

        TicketArtifactFields fields = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().backgroundImageUrl("https://api.example.com/api/v1/uploads/files/0b1f1b94-3a54-4b5e-9d2f-1f2b3c4d5e6f.png").build());

        assertThat(fields.design().backgroundImage()).isNotNull();
        assertThat(fields.design().backgroundImage().getWidth()).isEqualTo(20);
    }

    @Test
    void aQueryOrFragmentOnTheUploadUrl_isIgnored() throws Exception {
        when(imageStorageService.load("0b1f1b94-3a54-4b5e-9d2f-1f2b3c4d5e6f.png"))
                .thenReturn(new ImageStorageService.LoadedImage(new ByteArrayResource(png()), "image/png"));
        owner("Jamie Cruz");

        TicketArtifactFields fields = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().backgroundImageUrl("http://localhost:8081/api/v1/uploads/files/0b1f1b94-3a54-4b5e-9d2f-1f2b3c4d5e6f.png?v=2#x").build());

        assertThat(fields.design().backgroundImage()).isNotNull();
    }

    @Test
    void anyOtherUrl_isNeverFetched_andTheTicketRendersWithoutTheImage() {
        owner("Jamie Cruz");

        TicketArtifactFields fields = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().backgroundImageUrl("https://evil.example.com/cat.png").build());

        assertThat(fields.design()).isNotNull();
        assertThat(fields.design().backgroundImage()).isNull();
        verify(imageStorageService, never()).load(anyString());
    }

    @Test
    void aMissingUploadedFile_rendersWithoutTheImage_insteadOfFailing() {
        when(imageStorageService.load(anyString())).thenThrow(new ResourceNotFoundException("Image not found"));
        owner("Jamie Cruz");

        TicketArtifactFields fields = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().backgroundImageUrl("https://x/api/v1/uploads/files/0b1f1b94-3a54-4b5e-9d2f-1f2b3c4d5e6f.png").build());

        assertThat(fields.design().backgroundImage()).isNull();
    }

    @Test
    void anImageJavaCannotDecode_rendersWithoutTheImage() {
        when(imageStorageService.load(anyString()))
                .thenReturn(new ImageStorageService.LoadedImage(new ByteArrayResource("not an image".getBytes()), "image/webp"));
        owner("Jamie Cruz");

        TicketArtifactFields fields = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().backgroundImageUrl("https://x/api/v1/uploads/files/0b1f1b94-3a54-4b5e-9d2f-1f2b3c4d5e6f.webp").build());

        assertThat(fields.design().backgroundImage()).isNull();
    }

    @Test
    void theFitAndRectangleAreHandedOn() {
        owner("Jamie Cruz");

        TicketArtifactFields fields = render(ticket(null), event("UTC", null, Instant.parse("2026-12-24T01:30:00Z")),
                template().backgroundFit(BackgroundFit.CUSTOM).backgroundX(-10.0).backgroundY(5.0).backgroundWidth(120.0)
                        .backgroundHeight(110.0).backgroundColor("#123456").build());

        assertThat(fields.design().backgroundFit()).isEqualTo(BackgroundFit.CUSTOM);
        assertThat(fields.design().customRect()).isEqualTo(new TicketDesign.Rect(-10.0, 5.0, 120.0, 110.0));
        assertThat(fields.design().backgroundColorHex()).isEqualTo("#123456");
    }
}
