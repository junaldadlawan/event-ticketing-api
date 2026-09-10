package com.junaldadlawan.event_ticketing_api.tickettype;

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
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real SecurityConfig/JwtAuthenticationFilter wiring end to end
 * for the ticket-type module — mirrors
 * {@code EventOrganizationAccessIntegrationTest}/{@code VenueSecurityIntegrationTest}'s
 * style. Covers the golden path from the task: owner creates a ticket type on
 * a DRAFT event -> cross-org owner 403 on view/update -> publish the event ->
 * anonymous GET (no Authorization header) now succeeds; plus the six-field
 * partial-update matrix and the quantityAvailable resync edge case at the
 * HTTP layer.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TicketTypeAccessIntegrationTest {

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
    private ObjectMapper objectMapper;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTicketTypeIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID ticketTypeId : createdTicketTypeIds) {
            ticketTypeRepository.deleteById(ticketTypeId);
        }
        createdTicketTypeIds.clear();
        for (UUID eventId : createdEventIds) {
            eventRepository.deleteById(eventId);
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

    private UUID persistOrganization(UUID ownerId, OrganizationStatus status) {
        Organization organization = Organization.builder()
                .name("Ticket Type Access Test Org " + UUID.randomUUID())
                .status(status)
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
        OrganizationMember saved = organizationMemberRepository.save(member);
        createdMembers.add(saved);
    }

    private UUID persistEvent(UUID organizationId, EventStatus status) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = Event.builder()
                .organizationId(organizationId)
                .title("Ticket Type Access Test Event")
                .description("desc")
                .category("music")
                .status(status)
                .ticketPrefix("T" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private String createTicketTypeBody() {
        Instant saleStartAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(5, ChronoUnit.DAYS);
        return """
                {"name":"General Admission","kind":"GENERAL_ADMISSION",
                "price":{"amount":1000,"currency":"USD"},"quantityTotal":100,
                "saleStartAt":"%s","saleEndAt":"%s"}
                """.formatted(saleStartAt, saleEndAt);
    }

    private MvcResult createTicketType(UUID eventId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(createTicketTypeBody()))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private UUID trackCreatedTicketType(MvcResult result) throws Exception {
        UUID ticketTypeId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdTicketTypeIds.add(ticketTypeId);
        return ticketTypeId;
    }

    @Test
    void goldenPath_createDraftVisibility_publishMakesPublic() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);

        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);

        UUID otherOrgId = persistOrganization(otherOrgOwner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);

        String ownerToken = jwtService.generateAccessToken(owner);
        String strangerToken = jwtService.generateAccessToken(stranger);
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);

        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);

        // Step 1: a stranger cannot create a ticket type on this event.
        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content(createTicketTypeBody()))
                .andExpect(status().isForbidden());

        // Step 2 (key regression): an owner of a DIFFERENT org is also forbidden,
        // not just a roleless stranger.
        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken)
                        .contentType("application/json")
                        .content(createTicketTypeBody()))
                .andExpect(status().isForbidden());

        // Step 3: owner creates -> 201, quantityAvailable == quantityTotal, maxPerOrder defaults to 10.
        MvcResult createResult = createTicketType(eventId, ownerToken);
        UUID ticketTypeId = trackCreatedTicketType(createResult);
        var json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        assertThat(json.get("quantityTotal").asInt()).isEqualTo(100);
        assertThat(json.get("quantityAvailable").asInt()).isEqualTo(100);
        assertThat(json.get("maxPerOrder").asInt()).isEqualTo(10);

        // Step 4: draft event -> owner can GET/list.
        mockMvc.perform(get("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + ticketTypeId + "')]").exists());

        // Step 5: draft event -> stranger/anonymous/cross-org-owner all 403 on GET.
        mockMvc.perform(get("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId))
                .andExpect(status().isForbidden());

        // Step 6 (key regression): a different org's owner cannot PATCH either.
        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Hijacked"}
                                """))
                .andExpect(status().isForbidden());

        // Step 7: owner can PATCH.
        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Renamed By Owner"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed By Owner"));

        // Step 8: publish the event.
        mockMvc.perform(post("/api/v1/events/{eventId}/publish", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Step 9: now GET succeeds with NO Authorization header at all, for both
        // the single-resource and list endpoints.
        mockMvc.perform(get("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed By Owner"));
        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + ticketTypeId + "')]").exists());
    }

    @Test
    void create_nonExistentEvent_returns404() throws Exception {
        User someone = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(someone);

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(createTicketTypeBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_saleEndAtBeforeSaleStartAt_returns400() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);

        Instant saleStartAt = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(1, ChronoUnit.DAYS);
        String body = """
                {"name":"GA","kind":"GENERAL_ADMISSION","price":{"amount":1000,"currency":"USD"},
                "quantityTotal":100,"saleStartAt":"%s","saleEndAt":"%s"}
                """.formatted(saleStartAt, saleEndAt);

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_organizerAndAdmin_alsoSucceed() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User organizer = inMemoryUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);

        String organizerToken = jwtService.generateAccessToken(organizer);
        String adminToken = jwtService.generateAccessToken(admin);

        trackCreatedTicketType(createTicketType(eventId, organizerToken));
        // Admin has NO OrganizationMember row at all (BR-AUTH-004 bypass).
        trackCreatedTicketType(createTicketType(eventId, adminToken));
    }

    /**
     * Six updatable fields: name, price, quantityTotal, saleStartAt, saleEndAt,
     * maxPerOrder — NOT kind (create-time-only, no field on the update DTO).
     */
    @Test
    void update_sixFieldsIndividually_eachAppliesIndependently() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        UUID ticketTypeId = trackCreatedTicketType(createTicketType(eventId, ownerToken));

        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"VIP"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("VIP"))
                .andExpect(jsonPath("$.quantityTotal").value(100));

        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"price":{"amount":2500,"currency":"EUR"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.amount").value(2500))
                .andExpect(jsonPath("$.price.currency").value("EUR"))
                .andExpect(jsonPath("$.name").value("VIP"));

        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"maxPerOrder":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPerOrder").value(3));

        // Truncated to millis: Postgres TIMESTAMPTZ round-trips millis exactly,
        // but a bare Instant.now() can carry finer-than-millis precision that
        // wouldn't compare equal to the DB-round-tripped value in the response.
        Instant newSaleStartAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant newSaleEndAt = Instant.now().plus(9, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"saleStartAt":"%s","saleEndAt":"%s"}
                                """.formatted(newSaleStartAt, newSaleEndAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleStartAt").value(newSaleStartAt.toString()))
                .andExpect(jsonPath("$.saleEndAt").value(newSaleEndAt.toString()));

        // quantityTotal update -> quantityAvailable resyncs to the new value.
        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"quantityTotal":250}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityTotal").value(250))
                .andExpect(jsonPath("$.quantityAvailable").value(250));

        // Omitting quantityTotal on a subsequent PATCH must leave quantityAvailable untouched.
        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"VIP Renamed Again"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityTotal").value(250))
                .andExpect(jsonPath("$.quantityAvailable").value(250));
    }

    @Test
    void update_blankName_returns400() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        UUID ticketTypeId = trackCreatedTicketType(createTicketType(eventId, ownerToken));

        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * Sale-window validation is re-checked against the merged final state:
     * changing only saleEndAt to something before the EXISTING saleStartAt
     * must still 400, even though saleEndAt on its own says nothing invalid.
     */
    @Test
    void update_saleEndAtOnly_beforeExistingSaleStartAt_returns400() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        UUID ticketTypeId = trackCreatedTicketType(createTicketType(eventId, ownerToken));

        // Existing saleStartAt is now()+1d (see createTicketTypeBody()); move
        // saleEndAt to now()+12h, before it, without touching saleStartAt.
        Instant newSaleEndAt = Instant.now().plus(12, ChronoUnit.HOURS);
        mockMvc.perform(patch("/api/v1/ticket-types/{ticketTypeId}", ticketTypeId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"saleEndAt":"%s"}
                                """.formatted(newSaleEndAt)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noToken_cannotCreateTicketType() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-types", UUID.randomUUID())
                        .contentType("application/json")
                        .content(createTicketTypeBody()))
                .andExpect(status().isUnauthorized());
    }
}
