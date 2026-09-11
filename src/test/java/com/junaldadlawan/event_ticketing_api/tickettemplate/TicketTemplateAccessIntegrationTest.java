package com.junaldadlawan.event_ticketing_api.tickettemplate;

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
import com.junaldadlawan.event_ticketing_api.tickettemplate.repository.TicketTemplateRepository;
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
 * for the ticket-template module — mirrors {@code TicketTypeAccessIntegrationTest}/
 * {@code TicketAccessIntegrationTest}'s style. Closes the code-reviewer MEDIUM
 * finding: zero prior coverage for {@code tickettemplate/}. The two scenarios
 * the dispatch called out as this module's security-relevant behavior:
 * <ul>
 *   <li>{@code GET /events/{eventId}/ticket-templates} is owning-organizer/
 *   admin-only ALWAYS — a stranger is 403'd even on a PUBLISHED event, unlike
 *   {@code TicketType}'s draft-based visibility split.</li>
 *   <li>{@code PATCH /ticket-templates/{templateId}} resolves its owning
 *   organization strictly from the persisted template's own {@code eventId}
 *   — a cross-org organizer cannot hit it via a crafted request.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class TicketTemplateAccessIntegrationTest {

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
    private TicketTemplateRepository ticketTemplateRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTemplateIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID templateId : createdTemplateIds) {
            ticketTemplateRepository.deleteById(templateId);
        }
        createdTemplateIds.clear();
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

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Ticket Template Access Test Org " + UUID.randomUUID())
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
                .title("Ticket Template Access Test Event")
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

    private String createTemplateBody() {
        return """
                {"format":"DIGITAL","logoUrl":"https://example.com/logo.png",
                "backgroundImageUrl":"https://example.com/bg.png","primaryColor":"#ABCDEF"}
                """;
    }

    private MvcResult createTemplate(UUID eventId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(createTemplateBody()))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private UUID trackCreatedTemplate(MvcResult result) throws Exception {
        UUID templateId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdTemplateIds.add(templateId);
        return templateId;
    }

    // ---- create() ----

    @Test
    void create_owner_returns201_withBrandingPersisted() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);

        MvcResult result = createTemplate(eventId, ownerToken);
        trackCreatedTemplate(result);
        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("logoUrl").asText()).isEqualTo("https://example.com/logo.png");
        assertThat(json.get("backgroundImageUrl").asText()).isEqualTo("https://example.com/bg.png");
        assertThat(json.get("primaryColor").asText()).isEqualTo("#ABCDEF");
        assertThat(json.get("format").asText()).isEqualTo("DIGITAL");
    }

    @Test
    void create_organizerAndAdmin_alsoSucceed() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User organizer = inMemoryUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);

        trackCreatedTemplate(createTemplate(eventId, jwtService.generateAccessToken(organizer)));
        // Admin has NO OrganizationMember row at all (BR-AUTH-004 bypass).
        trackCreatedTemplate(createTemplate(eventId, jwtService.generateAccessToken(admin)));
    }

    @Test
    void create_stranger_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content(createTemplateBody()))
                .andExpect(status().isForbidden());
    }

    /** Key regression class: an owner of a DIFFERENT org is also forbidden, not just a stranger. */
    @Test
    void create_crossOrgOwner_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID otherOrgId = persistOrganization(otherOrgOwner.getId());
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken)
                        .contentType("application/json")
                        .content(createTemplateBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", UUID.randomUUID())
                        .contentType("application/json")
                        .content(createTemplateBody()))
                .andExpect(status().isUnauthorized());
    }

    // ---- list() : owning-organizer/admin-only ALWAYS, even on a published event ----

    /**
     * The key differentiator from {@code TicketType}'s list visibility: no
     * public/draft-based split exists here at all.
     */
    @Test
    void list_stranger_returns403_evenOnPublishedEvent() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        trackCreatedTemplate(createTemplate(eventId, ownerToken));

        // Publish the event.
        mockMvc.perform(post("/api/v1/events/{eventId}/publish", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        String strangerToken = jwtService.generateAccessToken(stranger);
        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
        // Anonymous (no token at all) is also forbidden - not merely unauthenticated
        // in a different sense - this endpoint requires a real 403-worthy role check
        // once authenticated; without any token it's a 401 at the filter level.
        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_owner_returns200() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        UUID templateId = trackCreatedTemplate(createTemplate(eventId, ownerToken));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + templateId + "')]").exists());
    }

    @Test
    void list_admin_returns200_withNoOrganizationMembershipAtAll() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        trackCreatedTemplate(createTemplate(eventId, ownerToken));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
                .andExpect(status().isOk());
    }

    // ---- update() : cross-org organizer cannot hit it via a crafted request ----

    @Test
    void update_owner_returns200_partialUpdate() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        UUID templateId = trackCreatedTemplate(createTemplate(eventId, ownerToken));

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"primaryColor":"#000000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColor").value("#000000"))
                // logoUrl/backgroundImageUrl untouched (null = unchanged)
                .andExpect(jsonPath("$.logoUrl").value("https://example.com/logo.png"))
                .andExpect(jsonPath("$.backgroundImageUrl").value("https://example.com/bg.png"));
    }

    /**
     * The dispatch's specifically-requested regression: a cross-org
     * organizer crafting a PATCH request against another org's template must
     * be rejected — proving resolution comes from the persisted template's
     * own {@code eventId}, not any client input (the update DTO carries no
     * event/organization identifier at all to spoof).
     */
    @Test
    void update_crossOrgOrganizer_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User otherOrgOrganizer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        UUID templateId = trackCreatedTemplate(createTemplate(eventId, ownerToken));

        UUID otherOrgId = persistOrganization(otherOrgOrganizer.getId());
        grantOrgRole(otherOrgOrganizer.getId(), otherOrgId, OrganizationRole.ORGANIZER);
        String otherOrgOrganizerToken = jwtService.generateAccessToken(otherOrgOrganizer);

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .header("Authorization", "Bearer " + otherOrgOrganizerToken)
                        .contentType("application/json")
                        .content("""
                                {"primaryColor":"#HIJACKED"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_unknownTemplate_returns404() throws Exception {
        User someone = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(someone);

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"primaryColor":"#000000"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_admin_returns200_withNoOrganizationMembershipAtAll() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID eventId = persistEvent(orgId, EventStatus.DRAFT);
        UUID templateId = trackCreatedTemplate(createTemplate(eventId, ownerToken));

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin))
                        .contentType("application/json")
                        .content("""
                                {"primaryColor":"#123123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColor").value("#123123"));
    }
}
