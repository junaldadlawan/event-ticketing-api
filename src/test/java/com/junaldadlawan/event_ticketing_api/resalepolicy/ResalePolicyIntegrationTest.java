package com.junaldadlawan.event_ticketing_api.resalepolicy;

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
import com.junaldadlawan.event_ticketing_api.resalepolicy.repository.ResalePolicyRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} coverage for {@code GET/PATCH
 * /events/{eventId}/resale-policy} against real Postgres + real signed JWTs
 * — mirrors {@code TicketTemplateAccessIntegrationTest}'s owner/organizer/
 * admin/stranger/cross-org matrix. Also verifies the dispatch's specific
 * claim that no {@code SecurityConfig} changes were needed: {@code GET} is
 * genuinely public (no token at all) and {@code PATCH} genuinely requires
 * auth, purely via the pre-existing broad matchers.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResalePolicyIntegrationTest {

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
    private ResalePolicyRepository resalePolicyRepository;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdPolicyIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdPolicyIds) {
            resalePolicyRepository.deleteById(id);
        }
        createdPolicyIds.clear();
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
        return User.builder().id(UUID.randomUUID()).email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Resale Policy Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED)
                .ownerId(ownerId)
                .documents(List.of())
                .build();
        Organization saved = organizationRepository.save(organization);
        createdOrgIds.add(saved.getId());
        return saved.getId();
    }

    private void grantOrgRole(UUID userId, UUID organizationId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(organizationId).build();
        member.getRoles().add(role);
        createdMembers.add(organizationMemberRepository.save(member));
    }

    private UUID persistEvent(UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = Event.builder()
                .organizationId(organizationId)
                .title("Resale Policy Test Event")
                .description("desc")
                .category("music")
                .status(EventStatus.PUBLISHED)
                .ticketPrefix("R" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private void trackPolicyForEvent(UUID eventId) {
        resalePolicyRepository.findByEventId(eventId).ifPresent(p -> createdPolicyIds.add(p.getId()));
    }

    // ---- GET (public) ----

    @Test
    void get_noPolicyYet_noTokenAtAll_returns200_disabledDefault() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);

        mockMvc.perform(get("/api/v1/events/{eventId}/resale-policy", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void get_unknownEvent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventId}/resale-policy", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ---- PATCH authorization matrix ----

    @Test
    void update_owner_returns200_enablesResaleWithFaceValueCap() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(patch("/api/v1/events/{eventId}/resale-policy", eventId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"priceCapRule\":\"FACE_VALUE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.priceCapRule").value("FACE_VALUE"));
        trackPolicyForEvent(eventId);

        // Round trip: GET now reflects the persisted policy.
        mockMvc.perform(get("/api/v1/events/{eventId}/resale-policy", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.priceCapRule").value("FACE_VALUE"));
    }

    @Test
    void update_organizer_returns200_withFeeCap() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User organizer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        UUID eventId = persistEvent(orgId);
        String organizerToken = jwtService.generateAccessToken(organizer);

        mockMvc.perform(patch("/api/v1/events/{eventId}/resale-policy", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"priceCapRule\":\"FACE_VALUE_PLUS_FEE\",\"feeAmount\":{\"amount\":500,\"currency\":\"USD\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeAmount.amount").value(500))
                .andExpect(jsonPath("$.feeAmount.currency").value("USD"));
        trackPolicyForEvent(eventId);
    }

    @Test
    void update_admin_returns200_withNoOrganizationMembershipAtAll() throws Exception {
        UUID orgId = persistOrganization(UUID.randomUUID());
        UUID eventId = persistEvent(orgId);
        User admin = inMemoryUser(Role.ADMIN);
        String adminToken = jwtService.generateAccessToken(admin);

        mockMvc.perform(patch("/api/v1/events/{eventId}/resale-policy", eventId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        trackPolicyForEvent(eventId);
    }

    @Test
    void update_roselessStranger_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(patch("/api/v1/events/{eventId}/resale-policy", eventId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_crossOrgOwner_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User otherOrgOwner = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID otherOrgId = persistOrganization(otherOrgOwner.getId());
        grantOrgRole(otherOrgOwner.getId(), otherOrgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        String otherOrgOwnerToken = jwtService.generateAccessToken(otherOrgOwner);

        mockMvc.perform(patch("/api/v1/events/{eventId}/resale-policy", eventId)
                        .header("Authorization", "Bearer " + otherOrgOwnerToken)
                        .contentType("application/json")
                        .content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_noToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/events/{eventId}/resale-policy", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_unknownEvent_returns404() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(patch("/api/v1/events/{eventId}/resale-policy", UUID.randomUUID())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"enabled\":true}"))
                .andExpect(status().isNotFound());
    }
}
