package com.junaldadlawan.event_ticketing_api.venue;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real SecurityConfig/JwtAuthenticationFilter wiring end to end
 * for the venue module — mirrors {@code OrganizationSecurityIntegrationTest}
 * and {@code EventOrganizationAccessIntegrationTest}. Uses in-memory
 * (unpersisted) {@link User} objects to sign real tokens via the real
 * {@link JwtService} bean (the filter never looks the subject up in the DB),
 * but persists real {@link Organization}/{@link OrganizationMember} rows
 * since {@code VenueServiceImpl.create}/{@code list} query
 * {@code OrganizationRepository.existsById} and the access guard for real.
 * <p>
 * Covers the golden path from the task: create -> list (member) / 403
 * (stranger) -> public GET with no Authorization header at all -> PATCH by
 * owner/organizer -> 403 for a stranger and for an owner/organizer of a
 * *different* organization (cross-org isolation) -> 404 for a nonexistent
 * venue on both GET and PATCH. No BR-VENUE-* / UC-VENUE-* docs exist; behavior
 * is per openapi.yaml's Venues paths (~lines 367-428) and schemas (~1728-1758).
 */
@SpringBootTest
@AutoConfigureMockMvc
class VenueSecurityIntegrationTest {

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
    private ObjectMapper objectMapper;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdVenueIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
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

    private UUID persistApprovedOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Venue Test Org " + UUID.randomUUID())
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
        OrganizationMember saved = organizationMemberRepository.save(member);
        createdMembers.add(saved);
    }

    private UUID createVenue(UUID orgId, String creatorToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations/{orgId}/venues", orgId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","address":"123 Main St","latitude":1.1,"longitude":2.2}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID venueId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdVenueIds.add(venueId);
        return venueId;
    }

    @Test
    void goldenPath_createListPublicGetPatch_crossOrgAndUnknownIdRejected() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User organizer = inMemoryUser(Role.CUSTOMER);
        User checkInStaffMember = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);

        UUID orgId = persistApprovedOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        grantOrgRole(checkInStaffMember.getId(), orgId, OrganizationRole.CHECK_IN_STAFF);

        UUID otherOrgId = persistApprovedOrganization(otherOrgOwner.getId());
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);

        String ownerToken = jwtService.generateAccessToken(owner);
        String organizerToken = jwtService.generateAccessToken(organizer);
        String memberToken = jwtService.generateAccessToken(checkInStaffMember);
        String strangerToken = jwtService.generateAccessToken(stranger);
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);

        // Step 1: org owner creates a venue.
        UUID venueId = createVenue(orgId, ownerToken, "Main Hall " + UUID.randomUUID());

        // Step 2: any member (here, CHECK_IN_STAFF) can list the org's venues.
        mockMvc.perform(get("/api/v1/organizations/{orgId}/venues", orgId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + venueId + "')]").exists());

        // Step 3: a stranger with no role in this org is forbidden from listing.
        mockMvc.perform(get("/api/v1/organizations/{orgId}/venues", orgId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        // Step 4 (the key thing to prove end-to-end): GET /venues/{id} succeeds
        // with NO Authorization header at all.
        mockMvc.perform(get("/api/v1/venues/{venueId}", venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(venueId.toString()));

        // Step 5: owner can PATCH.
        mockMvc.perform(patch("/api/v1/venues/{venueId}", venueId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Renamed By Owner"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed By Owner"));

        // Step 6: organizer of the same org can also PATCH.
        mockMvc.perform(patch("/api/v1/venues/{venueId}", venueId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType("application/json")
                        .content("""
                                {"address":"456 Renamed Ave"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("456 Renamed Ave"));

        // Step 7: a stranger with no role in this org gets 403 on PATCH.
        mockMvc.perform(patch("/api/v1/venues/{venueId}", venueId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Hijacked"}
                                """))
                .andExpect(status().isForbidden());

        // Step 8 (cross-org isolation): a user who IS owner of a *different*
        // organization also gets 403 — the authorization check is resolved
        // from the venue's own organization, not any org the caller belongs to.
        mockMvc.perform(patch("/api/v1/venues/{venueId}", venueId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Hijacked By Other Org Owner"}
                                """))
                .andExpect(status().isForbidden());

        // Step 9: nonexistent venue id returns 404 on both GET and PATCH.
        UUID unknownVenueId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/venues/{venueId}", unknownVenueId))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/venues/{venueId}", unknownVenueId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Doesn't Matter"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_nonMemberOfOrg_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistApprovedOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(post("/api/v1/organizations/{orgId}/venues", orgId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Should Not Be Created"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_checkInStaffOnly_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User checkInStaff = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistApprovedOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(checkInStaff.getId(), orgId, OrganizationRole.CHECK_IN_STAFF);
        String checkInStaffToken = jwtService.generateAccessToken(checkInStaff);

        mockMvc.perform(post("/api/v1/organizations/{orgId}/venues", orgId)
                        .header("Authorization", "Bearer " + checkInStaffToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Should Not Be Created"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_nonExistentOrganization_returns404() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(post("/api/v1/organizations/{orgId}/venues", UUID.randomUUID())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Doesn't Matter"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void noToken_cannotPatchVenue() throws Exception {
        mockMvc.perform(patch("/api/v1/venues/{venueId}", UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {"name":"Doesn't Matter"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
