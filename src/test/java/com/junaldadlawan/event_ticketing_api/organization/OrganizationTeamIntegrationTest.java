package com.junaldadlawan.event_ticketing_api.organization;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BR-ORG-007: the owner sees who the team is, changes members' roles and removes members. */
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationTeamIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;

    private final List<UUID> userIds = new ArrayList<>();
    private UUID orgId;
    private User owner;
    private User organizer;
    private User staff;
    private User member;

    @BeforeEach
    void setUp() {
        owner = persistUser("Olive", "Owner", "+639170000001");
        organizer = persistUser("Oscar", "Organizer", null);
        staff = persistUser("Stella", "Staff", "+639170000003");
        member = persistUser("Mona", "Member", "+639170000004");
        orgId = organizationRepository.save(Organization.builder()
                .name("Team Test Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(owner.getId()).documents(List.of()).build()).getId();
        grant(owner, OrganizationRole.OWNER);
        grant(organizer, OrganizationRole.ORGANIZER);
        grant(staff, OrganizationRole.CHECK_IN_STAFF);
        grant(member, OrganizationRole.ORGANIZER);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from audit_log_entries where organization_id = ?", orgId);
        jdbc.update("delete from organization_member_roles where organization_id = ?", orgId);
        jdbc.update("delete from organization_members where organization_id = ?", orgId);
        organizationRepository.deleteById(orgId);
        userIds.forEach(userRepository::deleteById);
    }

    @Test
    void theMemberList_namesEveryone_andShowsContactsOnlyToTheOwnerAndOrganizers() throws Exception {
        for (User viewer : List.of(owner, organizer)) {
            members(viewer).andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.userId=='%s')].name".formatted(member.getId())).value("Mona Member"))
                    .andExpect(jsonPath("$[?(@.userId=='%s')].email".formatted(member.getId())).value(member.getEmail()))
                    .andExpect(jsonPath("$[?(@.userId=='%s')].phoneNumber".formatted(member.getId())).value("+639170000004"));
        }
        // check-in-only staff see who is who, not how to reach them
        members(staff).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId=='%s')].name".formatted(member.getId())).value("Mona Member"))
                .andExpect(jsonPath("$[*].email", org.hamcrest.Matchers.everyItem(nullValue())))
                .andExpect(jsonPath("$[*].phoneNumber", org.hamcrest.Matchers.everyItem(nullValue())));
    }

    @Test
    void theOwner_changesAddsAndTakesAwayRoles_andEachChangeIsLogged() throws Exception {
        // organizer -> check-in staff
        setRoles(owner, member.getId(), "[\"CHECK_IN_STAFF\"]").andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("CHECK_IN_STAFF")))
                .andExpect(jsonPath("$.name").value("Mona Member"))
                .andExpect(jsonPath("$.email").value(member.getEmail()));
        // both roles
        setRoles(owner, member.getId(), "[\"ORGANIZER\",\"CHECK_IN_STAFF\"]").andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ORGANIZER", "CHECK_IN_STAFF")));
        // one of the two taken away
        setRoles(owner, member.getId(), "[\"ORGANIZER\"]").andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ORGANIZER")));
        // the same set again changes nothing and logs nothing
        setRoles(owner, member.getId(), "[\"ORGANIZER\"]").andExpect(status().isOk());

        List<Map<String, Object>> logged = jdbc.queryForList(
                "select details::text as details from audit_log_entries where organization_id = ? and action = 'organization_member.roles_changed'"
                        + " and target_id = ? order by created_at", orgId, member.getId());
        assertThat(logged).hasSize(3);
        assertThat((String) logged.get(0).get("details")).contains("\"name\": \"Mona Member\"")
                .contains("\"from\": [\"ORGANIZER\"]").contains("\"to\": [\"CHECK_IN_STAFF\"]");
    }

    @Test
    void invalidRoleSets_are400() throws Exception {
        setRoles(owner, member.getId(), "[]").andExpect(status().isBadRequest());
        setRoles(owner, member.getId(), "[\"OWNER\"]").andExpect(status().isBadRequest());
        setRoles(owner, member.getId(), "[\"ORGANIZER\",\"OWNER\"]").andExpect(status().isBadRequest());
        setRoles(owner, member.getId(), "[\"JANITOR\"]").andExpect(status().isBadRequest());
    }

    @Test
    void theOwnersMembership_cannotBeChangedOrRemoved() throws Exception {
        setRoles(owner, owner.getId(), "[\"ORGANIZER\"]").andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/organizations/{orgId}/members/{userId}", orgId, owner.getId())
                .header("Authorization", bearer(owner))).andExpect(status().isConflict());
    }

    @Test
    void onlyTheOwner_mayChangeOrRemove_andOnlyMembers() throws Exception {
        setRoles(organizer, member.getId(), "[\"CHECK_IN_STAFF\"]").andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/organizations/{orgId}/members/{userId}", orgId, member.getId())
                .header("Authorization", bearer(organizer))).andExpect(status().isForbidden());

        setRoles(owner, UUID.randomUUID(), "[\"ORGANIZER\"]").andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/organizations/{orgId}/members/{userId}", orgId, UUID.randomUUID())
                .header("Authorization", bearer(owner))).andExpect(status().isNotFound());
    }

    @Test
    void theOwner_removesAMember_andTheRemovalIsLogged() throws Exception {
        mockMvc.perform(delete("/api/v1/organizations/{orgId}/members/{userId}", orgId, member.getId())
                .header("Authorization", bearer(owner))).andExpect(status().isNoContent());

        assertThat(organizationMemberRepository.findByUserIdAndOrganizationId(member.getId(), orgId)).isEmpty();
        members(owner).andExpect(jsonPath("$[?(@.userId=='%s')]".formatted(member.getId())).isEmpty());
        String details = jdbc.queryForObject("select details::text from audit_log_entries where organization_id = ?"
                + " and action = 'organization_member.removed' and target_id = ?", String.class, orgId, member.getId());
        assertThat(details).contains("\"name\": \"Mona Member\"").contains("\"roles\": [\"ORGANIZER\"]");
    }

    // ---- helpers ----

    private ResultActions members(User viewer) throws Exception {
        return mockMvc.perform(get("/api/v1/organizations/{orgId}/members", orgId).header("Authorization", bearer(viewer)));
    }

    private ResultActions setRoles(User caller, UUID userId, String roles) throws Exception {
        return mockMvc.perform(put("/api/v1/organizations/{orgId}/members/{userId}/roles", orgId, userId)
                .header("Authorization", bearer(caller)).contentType("application/json").content("{\"roles\":" + roles + "}"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private User persistUser(String firstName, String lastName, String phone) {
        User saved = userRepository.save(User.builder().firstName(firstName).lastName(lastName).phoneNumber(phone)
                .email(firstName.toLowerCase() + "-" + UUID.randomUUID() + "@test.local").passwordHash("x").role(Role.CUSTOMER).build());
        userIds.add(saved.getId());
        return saved;
    }

    private void grant(User user, OrganizationRole role) {
        OrganizationMember membership = OrganizationMember.builder().userId(user.getId()).organizationId(orgId).build();
        membership.getRoles().add(role);
        organizationMemberRepository.save(membership);
    }
}
