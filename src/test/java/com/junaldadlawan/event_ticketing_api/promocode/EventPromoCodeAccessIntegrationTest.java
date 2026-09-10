package com.junaldadlawan.event_ticketing_api.promocode;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real SecurityConfig/JwtAuthenticationFilter wiring end to end
 * for the promo-code module — mirrors {@code TicketTypeAccessIntegrationTest}.
 * Covers UC-EVENT-06 / BR-PROMO-001/003/004 as implemented in Phase 5a:
 * owner/organizer/admin create, cross-org and stranger rejection, duplicate
 * code, the validUntil/validFrom ordering check, the PERCENTAGE
 * discountValue<=100 boundary (100 accepted, 101 rejected), and — the key
 * regression vs. TicketType's draft-based public visibility — that listing is
 * owner/organizer/admin ONLY, ALWAYS, even for a fully PUBLISHED event.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventPromoCodeAccessIntegrationTest {

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
    private PromoCodeRepository promoCodeRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdPromoCodeIds = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdOrgIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdPromoCodeIds) {
            promoCodeRepository.deleteById(id);
        }
        createdPromoCodeIds.clear();
        for (UUID id : createdEventIds) {
            eventRepository.deleteById(id);
        }
        createdEventIds.clear();
        for (OrganizationMember member : createdMembers) {
            organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                    .ifPresent(organizationMemberRepository::delete);
        }
        createdMembers.clear();
        for (UUID id : createdOrgIds) {
            organizationRepository.deleteById(id);
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
                .name("Promo Code Access Test Org " + UUID.randomUUID())
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

    private UUID persistEvent(UUID organizationId, EventStatus status) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = Event.builder()
                .organizationId(organizationId)
                .title("Promo Code Access Test Event")
                .description("desc")
                .category("music")
                .status(status)
                .ticketPrefix("P" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private String createBody(String code, int discountValue) {
        return """
                {"code":"%s","discountType":"PERCENTAGE","discountValue":%d,
                "validFrom":"%s","validUntil":"%s"}
                """.formatted(code, discountValue, Instant.now(), Instant.now().plus(5, ChronoUnit.DAYS));
    }

    private MvcResult createPromoCode(UUID eventId, String token, String code, int discountValue) throws Exception {
        return mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(createBody(code, discountValue)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private UUID trackCreated(MvcResult result) throws Exception {
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdPromoCodeIds.add(id);
        return id;
    }

    // ---- create() : authorization ----

    @Test
    void create_ownerOrganizerAdmin_allSucceed_strangerAndCrossOrgOwnerForbidden() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User organizer = inMemoryUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);
        User stranger = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);

        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        UUID otherOrgId = persistOrganization(otherOrgOwner.getId());
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);

        String ownerToken = jwtService.generateAccessToken(owner);
        String organizerToken = jwtService.generateAccessToken(organizer);
        String adminToken = jwtService.generateAccessToken(admin);
        String strangerToken = jwtService.generateAccessToken(stranger);
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);

        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);

        // Stranger and cross-org owner: 403.
        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content(createBody("STRANGER10", 10)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken)
                        .contentType("application/json")
                        .content(createBody("CROSSORG10", 10)))
                .andExpect(status().isForbidden());

        // Owner, organizer, admin (no org role at all — BR-AUTH-004): all succeed.
        trackCreated(createPromoCode(eventId, ownerToken, "OWNER10", 10));
        trackCreated(createPromoCode(eventId, organizerToken, "ORGANIZER10", 10));
        trackCreated(createPromoCode(eventId, adminToken, "ADMIN10", 10));
    }

    @Test
    void create_nonExistentEvent_returns404() throws Exception {
        User someone = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(someone);

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(createBody("SAVE10", 10)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", UUID.randomUUID())
                        .contentType("application/json")
                        .content(createBody("SAVE10", 10)))
                .andExpect(status().isUnauthorized());
    }

    // ---- create() : validation ----

    @Test
    void create_duplicateCodeForSameEvent_returns409() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);

        trackCreated(createPromoCode(eventId, ownerToken, "DUPE10", 10));

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content(createBody("DUPE10", 20)))
                .andExpect(status().isConflict());
    }

    @Test
    void create_validUntilBeforeValidFrom_returns400() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);

        Instant validFrom = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant validUntil = Instant.now().plus(1, ChronoUnit.DAYS);
        String body = """
                {"code":"BADWINDOW","discountType":"PERCENTAGE","discountValue":10,
                "validFrom":"%s","validUntil":"%s"}
                """.formatted(validFrom, validUntil);

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_validUntilEqualsValidFrom_returns400() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);

        Instant sameInstant = Instant.now().plus(1, ChronoUnit.DAYS);
        String body = """
                {"code":"SAMEWINDOW","discountType":"PERCENTAGE","discountValue":10,
                "validFrom":"%s","validUntil":"%s"}
                """.formatted(sameInstant, sameInstant);

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    /** Boundary: discountValue == 100 for PERCENTAGE must be ACCEPTED; 101 must be rejected. */
    @Test
    void create_percentageDiscountValueBoundary_100Accepted_101Rejected() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);

        MvcResult result = createPromoCode(eventId, ownerToken, "EXACT100", 100);
        trackCreated(result);
        // Confirm it round-tripped as exactly 100, not silently clamped.
        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(json.get("discountValue").asInt()).isEqualTo(100);

        mockMvc.perform(post("/api/v1/events/{eventId}/promo-codes", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content(createBody("OVER100", 101)))
                .andExpect(status().isBadRequest());
    }

    // ---- list() : owner/organizer/admin ONLY, ALWAYS — not gated by event visibility ----

    @Test
    void list_ownerOrganizerAdmin_succeedEvenOnPublishedEvent() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User organizer = inMemoryUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        String ownerToken = jwtService.generateAccessToken(owner);
        String organizerToken = jwtService.generateAccessToken(organizer);
        String adminToken = jwtService.generateAccessToken(admin);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        trackCreated(createPromoCode(eventId, ownerToken, "LIST10", 10));

        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId).header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    /**
     * Key regression vs. TicketType/SeatMap's draft-based public visibility:
     * promo-code listing stays owner/organizer/admin-only even once the event
     * is fully PUBLISHED — a stranger must still be forbidden, not silently
     * let in once the event goes public.
     */
    @Test
    void list_strangerOnPublishedEvent_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID otherOrgId = persistOrganization(otherOrgOwner.getId());
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);
        String strangerToken = jwtService.generateAccessToken(stranger);
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);

        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId).header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId).header("Authorization", "Bearer " + otherOrgOwnerToken))
                .andExpect(status().isForbidden());
        // Also unauthenticated (no token at all), unlike ticket-type's public GET for published events.
        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_unknownEvent_returns404() throws Exception {
        User someone = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(someone);

        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
