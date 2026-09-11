package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatMapRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import com.junaldadlawan.event_ticketing_api.tickettemplate.repository.TicketTemplateRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack {@code GET /tickets/{ticketId}/artifact} test — real
 * SecurityConfig/JwtAuthenticationFilter, real signed JWTs, real Postgres.
 * Closes the code-reviewer MEDIUM finding: zero prior coverage for {@code
 * ticket/artifact/}. This is the byte-level proof companion to {@code
 * TicketArtifactServiceImplTest} (which proves the same behaviors against
 * mocked repositories) — here everything is round-tripped through the real
 * HTTP response and real Postgres-persisted rows.
 * <p>
 * The single most important test in this class:
 * {@link #getArtifact_qrPayloadDecodesToActualCredential_notTicketNumberOrId()}
 * — decodes the QR from the real PNG response bytes and asserts it equals
 * the ticket's real {@code credential} column, read directly from Postgres.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TicketArtifactIntegrationTest {

    private static final Color DEFAULT_ACCENT = new Color(0x2B, 0x3A, 0x67);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private SeatMapRepository seatMapRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketTemplateRepository ticketTemplateRepository;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTicketTypeIds = new ArrayList<>();
    private final List<UUID> createdSeatMapIds = new ArrayList<>();
    private final List<UUID> createdSeatIds = new ArrayList<>();
    private final List<UUID> createdTicketIds = new ArrayList<>();
    private final List<UUID> createdTemplateIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdTicketIds) {
            ticketRepository.deleteById(id);
        }
        createdTicketIds.clear();
        for (UUID id : createdTemplateIds) {
            ticketTemplateRepository.deleteById(id);
        }
        createdTemplateIds.clear();
        for (UUID id : createdSeatIds) {
            seatRepository.deleteById(id);
        }
        createdSeatIds.clear();
        for (UUID id : createdSeatMapIds) {
            seatMapRepository.deleteById(id);
        }
        createdSeatMapIds.clear();
        for (UUID id : createdTicketTypeIds) {
            ticketTypeRepository.deleteById(id);
        }
        createdTicketTypeIds.clear();
        for (UUID id : createdEventIds) {
            eventRepository.deleteById(id);
        }
        createdEventIds.clear();
        for (OrganizationMember member : createdMembers) {
            organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                    .ifPresent(organizationMemberRepository::delete);
        }
        createdMembers.clear();
        for (UUID orgId : createdOrgIds) {
            organizationRepository.deleteById(orgId);
        }
        createdOrgIds.clear();
    }

    private User inMemoryUser(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local")
                .role(role)
                .build();
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Ticket Artifact Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED)
                .ownerId(ownerId)
                .documents(List.of())
                .build();
        Organization saved = organizationRepository.save(organization);
        createdOrgIds.add(saved.getId());
        return saved.getId();
    }

    private void grantOrgRole(UUID userId, UUID organizationId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder()
                .userId(userId)
                .organizationId(organizationId)
                .build();
        member.getRoles().add(role);
        createdMembers.add(organizationMemberRepository.save(member));
    }

    private UUID persistEvent(UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = Event.builder()
                .organizationId(organizationId)
                .title("Ticket Artifact Test Event")
                .description("desc")
                .category("music")
                .status(EventStatus.PUBLISHED)
                .ticketPrefix("T" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistTicketType(UUID eventId) {
        return persistTicketType(eventId, "General Admission", TicketTypeKind.GENERAL_ADMISSION);
    }

    /**
     * Named parameter deliberately distinct from the literal string
     * "General Admission" when exercising the reserved-seating path — the
     * ticket type's own {@code name} field is drawn onto the artifact
     * regardless of seating kind, so reusing "General Admission" there would
     * make a seat-vs-GA text assertion meaningless (it would always contain
     * that phrase via the ticket type name, independent of the actual
     * seat-description field under test).
     */
    private UUID persistTicketType(UUID eventId, String name, TicketTypeKind kind) {
        Instant saleStartAt = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(5, ChronoUnit.DAYS);
        TicketType ticketType = TicketType.builder()
                .eventId(eventId)
                .name(name)
                .kind(kind)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .quantityTotal(100)
                .quantityAvailable(100)
                .saleStartAt(saleStartAt)
                .saleEndAt(saleEndAt)
                .maxPerOrder(10)
                .build();
        TicketType saved = ticketTypeRepository.save(ticketType);
        createdTicketTypeIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistSeat(UUID eventId) {
        SeatMap seatMap = seatMapRepository.save(SeatMap.builder().eventId(eventId).build());
        createdSeatMapIds.add(seatMap.getId());
        Seat seat = seatRepository.save(Seat.builder()
                .seatMapId(seatMap.getId())
                .section("A")
                .row("7")
                .seatNumber("21")
                .status(SeatStatus.SOLD)
                .build());
        createdSeatIds.add(seat.getId());
        return seat.getId();
    }

    private UUID persistTicket(UUID eventId, UUID ticketTypeId, UUID seatId, UUID ownerId, String credential) {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .orderId(UUID.randomUUID())
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .seatId(seatId)
                .ownerId(ownerId)
                .ticketNumber("TCK-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential(credential)
                .status(TicketStatus.VALID)
                .build();
        Ticket saved = ticketRepository.save(ticket);
        createdTicketIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistTemplate(UUID eventId, UUID ticketTypeIdOrNull, TicketTemplateFormat format, String primaryColor) {
        TicketTemplate template = TicketTemplate.builder()
                .eventId(eventId)
                .ticketTypeId(ticketTypeIdOrNull)
                .format(format)
                .primaryColor(primaryColor)
                .build();
        TicketTemplate saved = ticketTemplateRepository.save(template);
        createdTemplateIds.add(saved.getId());
        return saved.getId();
    }

    /**
     * Unlike {@code QrCodeGeneratorTest}/{@code TicketArtifactServiceImplTest}
     * (which decode the pure, standalone QR image), this scans the FULL
     * composited ticket PNG - the QR is one region among event-title text,
     * an accent bar, etc. {@code TRY_HARDER} was found necessary: without
     * it, {@code MultiFormatReader} intermittently failed to locate the
     * finder patterns in the larger, busier image (flaky {@code
     * NotFoundException}, not a real encoding/rendering defect - the same
     * PNG bytes always decode correctly once this hint is set).
     */
    private String decodeQr(byte[] pngBytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        return new MultiFormatReader().decode(bitmap, hints).getText();
    }

    // ---- format handling ----

    @Test
    void getArtifact_digitalFormat_returns200_pngContentType_genuinelyImageIOReadable() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        UUID ticketId = persistTicket(eventId, ticketTypeId, null, buyer.getId(), "cred-" + UUID.randomUUID());
        String buyerToken = jwtService.generateAccessToken(buyer);

        MvcResult result = mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("format", "digital"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andReturn();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
        assertThat(image).isNotNull();
    }

    @Test
    void getArtifact_physicalFormat_returns200_pdfContentType_genuinelyPdfBoxLoadable_exactlyOnePage() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        UUID ticketId = persistTicket(eventId, ticketTypeId, null, buyer.getId(), "cred-" + UUID.randomUUID());
        String buyerToken = jwtService.generateAccessToken(buyer);

        MvcResult result = mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("format", "physical"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn();

        try (PDDocument document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void getArtifact_invalidFormat_returns400() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(buyer.getId());
        grantOrgRole(buyer.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        UUID ticketId = persistTicket(eventId, ticketTypeId, null, buyer.getId(), "cred-" + UUID.randomUUID());
        String token = jwtService.generateAccessToken(buyer);

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId)
                        .header("Authorization", "Bearer " + token)
                        .param("format", "xyz"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Regression test for the {@code GlobalExceptionHandler} fix shipped in
     * this same dispatch (no test for {@code MissingServletRequestParameterException}
     * existed anywhere in the suite before this). Proves the real filter
     * chain authenticates the caller first (so this is genuinely 400, not
     * 401) and only THEN 400s on the missing required query parameter.
     */
    @Test
    void getArtifact_missingFormatParam_returns400_notUnauthorized() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(buyer.getId());
        grantOrgRole(buyer.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        UUID ticketId = persistTicket(eventId, ticketTypeId, null, buyer.getId(), "cred-" + UUID.randomUUID());
        String token = jwtService.generateAccessToken(buyer);

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getArtifact_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", UUID.randomUUID())
                        .param("format", "digital"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getArtifact_stranger_returns403() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        UUID ticketId = persistTicket(eventId, ticketTypeId, null, buyer.getId(), "cred-" + UUID.randomUUID());
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .param("format", "digital"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getArtifact_unknownTicket_returns404() throws Exception {
        User someone = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(someone);

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .param("format", "digital"))
                .andExpect(status().isNotFound());
    }

    // ---- THE most important test: QR payload correctness ----

    /**
     * Generates a real digital artifact, decodes the QR from the actual PNG
     * response bytes via ZXing, and asserts it equals the ticket's real
     * {@code credential} column (read directly from Postgres, since the API
     * never returns it) — NOT {@code ticketNumber}, NOT {@code id}.
     */
    @Test
    void getArtifact_qrPayloadDecodesToActualCredential_notTicketNumberOrId() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        String realCredential = UUID.randomUUID() + "." + "genuinelySecretHmacSignature";
        UUID ticketId = persistTicket(eventId, ticketTypeId, null, buyer.getId(), realCredential);
        String buyerToken = jwtService.generateAccessToken(buyer);

        MvcResult result = mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("format", "digital"))
                .andExpect(status().isOk())
                .andReturn();

        String decoded = decodeQr(result.getResponse().getContentAsByteArray());

        Ticket persisted = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(persisted.getCredential()).isEqualTo(realCredential);
        assertThat(decoded).isEqualTo(realCredential);
        assertThat(decoded).isNotEqualTo(persisted.getTicketNumber());
        assertThat(decoded).isNotEqualTo(persisted.getId().toString());
    }

    // ---- template resolution order, proven via the rendered accent-bar pixel color ----

    @Test
    void getArtifact_templateResolution_ticketTypeSpecificPreferredOverEventLevel() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        UUID ticketId = persistTicket(eventId, ticketTypeId, null, buyer.getId(), "cred-" + UUID.randomUUID());
        // BOTH an event-level template AND a ticket-type-specific template
        // for the SAME event+format - the specific one must win.
        persistTemplate(eventId, null, TicketTemplateFormat.DIGITAL, "#00FF00");
        persistTemplate(eventId, ticketTypeId, TicketTemplateFormat.DIGITAL, "#FF0000");
        String buyerToken = jwtService.generateAccessToken(buyer);

        MvcResult result = mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("format", "digital"))
                .andExpect(status().isOk())
                .andReturn();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
        // The accent bar occupies the top 70px (see PngTicketRenderer) - sample
        // well inside it, away from any text glyphs.
        Color pixel = new Color(image.getRGB(10, 10));
        assertThat(pixel).isEqualTo(Color.decode("#FF0000"));
    }

    @Test
    void getArtifact_templateResolution_eventLevelOnly_usedWhenNoTicketTypeSpecificTemplate() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        UUID ticketId = persistTicket(eventId, ticketTypeId, null, buyer.getId(), "cred-" + UUID.randomUUID());
        persistTemplate(eventId, null, TicketTemplateFormat.DIGITAL, "#0000FF");
        String buyerToken = jwtService.generateAccessToken(buyer);

        MvcResult result = mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("format", "digital"))
                .andExpect(status().isOk())
                .andReturn();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
        Color pixel = new Color(image.getRGB(10, 10));
        assertThat(pixel).isEqualTo(Color.decode("#0000FF"));
    }

    @Test
    void getArtifact_templateResolution_noTemplateAtAll_stillRenders200_withDefaultAccent() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        UUID ticketId = persistTicket(eventId, ticketTypeId, null, buyer.getId(), "cred-" + UUID.randomUUID());
        String buyerToken = jwtService.generateAccessToken(buyer);

        MvcResult result = mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("format", "digital"))
                .andExpect(status().isOk())
                .andReturn();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
        Color pixel = new Color(image.getRGB(10, 10));
        assertThat(pixel).isEqualTo(DEFAULT_ACCENT);
    }

    // ---- seat info vs GA rendering, proven via extractable PDF text ----

    @Test
    void getArtifact_reservedSeatTicket_pdfTextContainsSectionRowSeatNumber() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        // Deliberately NOT named "General Admission" (see persistTicketType's
        // javadoc) so this test genuinely proves the seat-description field,
        // not an incidental match against the ticket type's own name.
        UUID ticketTypeId = persistTicketType(eventId, "VIP Reserved", TicketTypeKind.RESERVED_SEATING);
        UUID seatId = persistSeat(eventId);
        UUID ticketId = persistTicket(eventId, ticketTypeId, seatId, buyer.getId(), "cred-" + UUID.randomUUID());
        String buyerToken = jwtService.generateAccessToken(buyer);

        MvcResult result = mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("format", "physical"))
                .andExpect(status().isOk())
                .andReturn();

        String text;
        try (PDDocument document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            text = new PDFTextStripper().getText(document);
        }
        assertThat(text).contains("Section A").contains("Row 7").contains("Seat 21");
        assertThat(text).doesNotContain("General Admission");
    }

    @Test
    void getArtifact_gaTicket_pdfTextContainsGeneralAdmission() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        UUID ticketId = persistTicket(eventId, ticketTypeId, null, buyer.getId(), "cred-" + UUID.randomUUID());
        String buyerToken = jwtService.generateAccessToken(buyer);

        MvcResult result = mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("format", "physical"))
                .andExpect(status().isOk())
                .andReturn();

        String text;
        try (PDDocument document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            text = new PDFTextStripper().getText(document);
        }
        assertThat(text).contains("General Admission");
    }
}
