package com.junaldadlawan.event_ticketing_api.organization;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BR-ORG-008: per-member permissions for organizers. */
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationPermissionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private EventRepository eventRepository;

    private UUID orgId;
    private UUID eventId;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = user();
        orgId = organizationRepository.save(Organization.builder().name("Permission Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(owner.getId()).documents(List.of()).build()).getId();
        member(owner, Set.of(OrganizationRole.OWNER), null);
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        eventId = eventRepository.save(Event.builder().organizationId(orgId).title("Permission Test Event").description("desc")
                .category("music").status(EventStatus.DRAFT).ticketPrefix("Q" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from audit_log_entries where organization_id = ?", orgId);
        jdbc.update("delete from organization_member_permissions where organization_id = ?", orgId);
        jdbc.update("delete from organization_member_roles where organization_id = ?", orgId);
        jdbc.update("delete from organization_members where organization_id = ?", orgId);
        eventRepository.deleteById(eventId);
        organizationRepository.deleteById(orgId);
    }

    @Test
    void organizers_getEveryPermissionButTeam_byDefault_andTheOwnerHoldsAll() throws Exception {
        // every organizer that existed before V62 was given the defaults
        assertThat(jdbc.queryForObject("select count(*) from organization_member_roles r where r.role = 'ORGANIZER'"
                + " and not exists (select 1 from organization_member_permissions p where p.user_id = r.user_id"
                + " and p.organization_id = r.organization_id)", Integer.class)).isZero();

        User organizer = user();
        member(organizer, Set.of(OrganizationRole.ORGANIZER), null);
        me(organizer).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("EVENTS", "PROMO_CODES", "CHECK_IN", "ORDERS_REFUNDS", "FINANCE", "ANALYTICS")));
        me(owner).andExpect(jsonPath("$.permissions", hasItem("TEAM"))).andExpect(jsonPath("$.permissions.length()").value(7));
    }

    @Test
    void anOrganizerWithoutAPermission_is403_forThatArea() throws Exception {
        User eventsOnly = user();
        member(eventsOnly, Set.of(OrganizationRole.ORGANIZER), Set.of(OrganizationPermission.EVENTS));
        User fullOrganizer = user();
        member(fullOrganizer, Set.of(OrganizationRole.ORGANIZER), null);

        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId).header("Authorization", bearer(eventsOnly)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{eventId}/promo-codes", eventId).header("Authorization", bearer(fullOrganizer)))
                .andExpect(status().isOk());
        // EVENTS is enough to manage the event itself
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/events/{eventId}", eventId)
                        .header("Authorization", bearer(eventsOnly)).contentType("application/json").content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void withNobodyHoldingTeam_onlyTheOwnerManagesTheTeam() throws Exception {
        User organizer = user();
        member(organizer, Set.of(OrganizationRole.ORGANIZER), null);
        User staff = user();
        member(staff, Set.of(OrganizationRole.CHECK_IN_STAFF), null);

        setRoles(organizer, staff.getId(), "[\"ORGANIZER\"]", null).andExpect(status().isForbidden());
        setRoles(owner, staff.getId(), "[\"ORGANIZER\"]", "[\"EVENTS\"]").andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("EVENTS")));
        // permissions without ORGANIZER, or an unknown one, are 400
        setRoles(owner, staff.getId(), "[\"CHECK_IN_STAFF\"]", "[\"EVENTS\"]").andExpect(status().isBadRequest());
        setRoles(owner, staff.getId(), "[\"ORGANIZER\"]", "[\"EVERYTHING\"]").andExpect(status().isBadRequest());

        String logged = jdbc.queryForObject("select details::text from audit_log_entries where organization_id = ?"
                + " and action = 'organization_member.permissions_changed' and target_id = ?", String.class, orgId, staff.getId());
        assertThat(logged).contains("\"from\": []").contains("\"to\": [\"EVENTS\"]");
    }

    @Test
    void aTeamOrganizer_managesOthers_butNotTheOwner_notThemselves_andGrantsOnlyWhatTheyHold() throws Exception {
        User lead = user();
        member(lead, Set.of(OrganizationRole.ORGANIZER), Set.of(OrganizationPermission.TEAM, OrganizationPermission.EVENTS));
        User other = user();
        member(other, Set.of(OrganizationRole.ORGANIZER), Set.of(OrganizationPermission.EVENTS));

        setRoles(lead, other.getId(), "[\"ORGANIZER\",\"CHECK_IN_STAFF\"]", "[\"EVENTS\"]").andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ORGANIZER", "CHECK_IN_STAFF")));
        setRoles(lead, other.getId(), "[\"ORGANIZER\"]", "[\"EVENTS\",\"FINANCE\"]").andExpect(status().isForbidden());
        setRoles(lead, owner.getId(), "[\"ORGANIZER\"]", null).andExpect(status().isConflict());
        setRoles(lead, lead.getId(), "[\"ORGANIZER\"]", "[\"TEAM\",\"EVENTS\",\"FINANCE\"]").andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/organizations/{orgId}/members/{userId}", orgId, other.getId()).header("Authorization", bearer(lead)))
                .andExpect(status().isNoContent());
        me(other).andExpect(status().isNotFound());
        me(lead).andExpect(jsonPath("$.permissions", not(hasItem("FINANCE"))));
    }

    // ---- helpers ----

    private ResultActions me(User user) throws Exception {
        return mockMvc.perform(get("/api/v1/organizations/{orgId}/members/me", orgId).header("Authorization", bearer(user)));
    }

    private ResultActions setRoles(User caller, UUID userId, String roles, String permissions) throws Exception {
        String body = "{\"roles\":" + roles + (permissions == null ? "" : ",\"permissions\":" + permissions) + "}";
        return mockMvc.perform(put("/api/v1/organizations/{orgId}/members/{userId}/roles", orgId, userId)
                .header("Authorization", bearer(caller)).contentType("application/json").content(body));
    }

    private User user() {
        return User.builder().id(UUID.randomUUID()).email("perm-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    /** {@code permissions} null = nobody chose them (an organizer gets the defaults). */
    private void member(User user, Set<OrganizationRole> roles, Set<OrganizationPermission> permissions) {
        OrganizationMember membership = OrganizationMember.builder().userId(user.getId()).organizationId(orgId).build();
        membership.getRoles().addAll(roles);
        if (permissions != null) {
            membership.choosePermissions(permissions);
        }
        organizationMemberRepository.save(membership);
    }
}
