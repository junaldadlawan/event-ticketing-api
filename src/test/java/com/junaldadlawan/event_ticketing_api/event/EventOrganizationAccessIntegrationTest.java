package com.junaldadlawan.event_ticketing_api.event;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real SecurityConfig/JwtAuthenticationFilter wiring end to end
 * for the event module, mirroring {@code VenueSecurityIntegrationTest}'s
 * style. Replaces this class's original coarse-check coverage: Phase 3
 * replaced the "owner/organizer of *some* organization" SpEL check
 * ({@code OrganizationAccessGuard.isOwnerOrOrganizerAnywhere}, now removed)
 * with a strict, per-event, per-organization check resolved from the
 * persisted event's own {@code organizationId} — so the old
 * "any org owner/organizer, or admin-with-no-org-role, can create an event"
 * assertions are deliberately no longer true and are replaced below with the
 * opposite (cross-org / no-role callers must be rejected).
 * <p>
 * Covers the golden path from the plan's Verification section: create with a
 * real organizationId + venueId -> generated ticketPrefix + venue snapshot ->
 * cross-org/stranger rejection on create -> draft visibility (owner
 * GET/PATCH ok, stranger/anonymous 403) -> publish -> public GET with no
 * Authorization header -> cross-org PATCH/cancel/delete rejection (the key
 * regression proving the old "any org" gap is closed) -> cancel (202) ->
 * republish/re-cancel conflicts (409) -> venue-from-another-org rejection on
 * create (400).
 * <p>
 * Per BR-AUTH-004, create carries the same admin bypass as
 * update/publish/cancel/delete — an admin with no org role can still create
 * an event for any APPROVED organization.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventOrganizationAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdVenueIds = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
            eventRepository.deleteById(eventId);
        }
        createdEventIds.clear();
        for (UUID venueId : createdVenueIds) {
            venueRepository.deleteById(venueId);
        }
        createdVenueIds.clear();
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
                .name("Event Access Test Org " + UUID.randomUUID())
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

    private UUID persistVenue(UUID organizationId) {
        Venue venue = Venue.builder()
                .organizationId(organizationId)
                .name("Test Venue " + UUID.randomUUID())
                .address("123 Test St")
                .latitude(1.1)
                .longitude(2.2)
                .build();
        Venue saved = venueRepository.save(venue);
        createdVenueIds.add(saved.getId());
        return saved.getId();
    }

    private String eventRequestBody(UUID organizationId, UUID venueId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant endAt = startAt.plus(2, ChronoUnit.HOURS);
        String venueField = venueId == null ? "null" : "\"" + venueId + "\"";
        return """
                {"organizationId":"%s","title":"Org Access Regression Event","description":"desc","category":"music",
                "venueId":%s,"startAt":"%s","endAt":"%s","timezone":"UTC"}
                """.formatted(organizationId, venueField, startAt, endAt);
    }

    private MvcResult createEvent(UUID organizationId, UUID venueId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(eventRequestBody(organizationId, venueId)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private UUID trackCreatedEvent(MvcResult result) throws Exception {
        UUID eventId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdEventIds.add(eventId);
        return eventId;
    }

    @Test
    void goldenPath_createPublishCancel_crossOrgRejected() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);

        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID venueId = persistVenue(orgId);

        UUID otherOrgId = persistOrganization(otherOrgOwner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);

        String ownerToken = jwtService.generateAccessToken(owner);
        String strangerToken = jwtService.generateAccessToken(stranger);
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);

        // Step 1: a stranger with no role in the org is forbidden from creating.
        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content(eventRequestBody(orgId, venueId)))
                .andExpect(status().isForbidden());

        // Step 2: owner creates the event -> 201, generated 3-letter ticketPrefix, venue snapshot.
        MvcResult createResult = createEvent(orgId, venueId, ownerToken);
        UUID eventId = trackCreatedEvent(createResult);
        String body = createResult.getResponse().getContentAsString();
        var json = objectMapper.readTree(body);
        String ticketPrefix = json.get("ticketPrefix").asText();
        assertThat(ticketPrefix).matches("[A-Z]{3}");
        assertThat(json.get("venue").get("id").asText()).isEqualTo(venueId.toString());
        assertThat(json.get("status").asText()).isEqualTo("DRAFT");

        // Step 3: owner can GET the draft event.
        mockMvc.perform(get("/api/v1/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Step 4: a stranger gets 403 on GET of a draft (non-public) event.
        mockMvc.perform(get("/api/v1/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        // Step 5: a fully anonymous caller also gets 403 on a draft event (not a 500/NPE).
        mockMvc.perform(get("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isForbidden());

        // Step 6: owner can PATCH the draft event.
        mockMvc.perform(patch("/api/v1/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"title":"Renamed By Owner"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed By Owner"));

        // Step 7 (key regression): an owner of a DIFFERENT organization gets 403 on
        // PATCH — proving the old "any org owner can touch any event" gap is closed.
        mockMvc.perform(patch("/api/v1/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken)
                        .contentType("application/json")
                        .content("""
                                {"title":"Hijacked"}
                                """))
                .andExpect(status().isForbidden());

        // Step 8: owner publishes the event.
        mockMvc.perform(post("/api/v1/events/{eventId}/publish", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Step 9: now GET succeeds with NO Authorization header at all (public).
        mockMvc.perform(get("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isOk());

        // Step 10: a different org's owner still can't cancel/delete this event.
        mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken))
                .andExpect(status().isForbidden());

        // Step 11: publishing an already-published event returns 409.
        mockMvc.perform(post("/api/v1/events/{eventId}/publish", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        // Step 12: owner cancels -> 202, status now CANCELLED.
        mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Step 13: cancelling an already-cancelled event returns 409.
        mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        // Step 14: publishing a cancelled event returns 409.
        mockMvc.perform(post("/api/v1/events/{eventId}/publish", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }

    /**
     * The golden path above already proves cross-org-owner-403 on PATCH
     * (step 7) and cancel (step 10). This test proves the same for the two
     * remaining mutation surfaces: draft GET and DELETE. DELETE in
     * particular previously had NO per-event authorization check at all —
     * this is the second core fix of this phase, and the most important
     * thing to regression-guard here.
     */
    @Test
    void crossOrgOwner_forbiddenOnDraftGet_andOnDelete() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);

        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);

        UUID otherOrgId = persistOrganization(otherOrgOwner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);

        String ownerToken = jwtService.generateAccessToken(owner);
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);

        UUID eventId = trackCreatedEvent(createEvent(orgId, null, ownerToken));

        // A different org's OWNER (not just a roleless stranger) gets 403 on
        // GET of the still-draft event.
        mockMvc.perform(get("/api/v1/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken))
                .andExpect(status().isForbidden());

        // Same other-org owner gets 403 on DELETE.
        mockMvc.perform(delete("/api/v1/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken))
                .andExpect(status().isForbidden());

        // The event's own owner can still delete it -> 204.
        mockMvc.perform(delete("/api/v1/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void create_venueFromDifferentOrganization_returns400() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User otherOwner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID otherOrgId = persistOrganization(otherOwner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(otherOwner.getId(), otherOrgId, OrganizationRole.OWNER);
        UUID venueFromOtherOrg = persistVenue(otherOrgId);

        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content(eventRequestBody(orgId, venueFromOtherOrg)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_organizationNotApproved_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.PENDING);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content(eventRequestBody(orgId, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_nonExistentOrganization_returns404() throws Exception {
        User someone = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(someone);

        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(eventRequestBody(UUID.randomUUID(), null)))
                .andExpect(status().isNotFound());
    }

    /**
     * Per BR-AUTH-004 ("admins have platform-wide access, unscoped by
     * organization or event"), {@code createEvent} carries the same admin
     * bypass as get/update/publish/cancel/delete — an admin with no role in
     * the target organization can still create an event for it, as long as
     * the organization itself is APPROVED.
     */
    @Test
    void create_adminWithNoOrgRole_succeeds() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String adminToken = jwtService.generateAccessToken(admin);

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(eventRequestBody(orgId, null)))
                .andExpect(status().isCreated())
                .andReturn();
        trackCreatedEvent(result);
    }

    /**
     * update/publish/cancel/delete all explicitly allow an admin to act
     * regardless of org membership (same as create, per BR-AUTH-004).
     */
    @Test
    void admin_canUpdateAndCancelEventWithNoOrgRole() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User admin = inMemoryUser(Role.ADMIN);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);
        String adminToken = jwtService.generateAccessToken(admin);

        UUID eventId = trackCreatedEvent(createEvent(orgId, null, ownerToken));

        mockMvc.perform(patch("/api/v1/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {"title":"Renamed By Admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed By Admin"));

        mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted());
    }

    @Test
    void update_blankTitle_returns400() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId(), OrganizationStatus.APPROVED);
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String ownerToken = jwtService.generateAccessToken(owner);

        UUID eventId = trackCreatedEvent(createEvent(orgId, null, ownerToken));

        mockMvc.perform(patch("/api/v1/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"title":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noToken_cannotCreateEvent() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(eventRequestBody(UUID.randomUUID(), null)))
                .andExpect(status().isUnauthorized());
    }
}
