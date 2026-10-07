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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /organizations/mine}: the caller's own organizations of every status, through the real security chain. */
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationMineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    private final List<UUID> orgIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID orgId : orgIds) {
            organizationMemberRepository.deleteAll(organizationMemberRepository.findByOrganizationId(orgId));
            organizationRepository.deleteById(orgId);
        }
    }

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email("mine-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private UUID org(OrganizationStatus status) {
        Organization saved = organizationRepository.save(Organization.builder().name("Mine Test Org " + UUID.randomUUID())
                .status(status).documents(List.of()).build());
        orgIds.add(saved.getId());
        return saved.getId();
    }

    private void member(User user, UUID orgId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder().userId(user.getId()).organizationId(orgId).build();
        member.getRoles().add(role);
        organizationMemberRepository.save(member);
    }

    private JsonNode mine(User user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/organizations/mine").header("Authorization", token(user)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<String> ids(JsonNode list) {
        List<String> ids = new ArrayList<>();
        list.forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }

    @Test
    void anOwnerSeesASuspendedOrganizationThatHasNoEvents() throws Exception {
        User owner = user(Role.CUSTOMER);
        UUID suspended = org(OrganizationStatus.SUSPENDED);
        member(owner, suspended, OrganizationRole.OWNER);

        JsonNode list = mine(owner);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asText()).isEqualTo(suspended.toString());
        assertThat(list.get(0).get("status").asText()).isEqualTo("SUSPENDED");
        assertThat(list.get(0).get("name").asText()).startsWith("Mine Test Org");
    }

    @Test
    void organizersAndCheckInStaffSeeTheirOrganizations() throws Exception {
        User organizer = user(Role.CUSTOMER);
        User scanner = user(Role.CUSTOMER);
        UUID approved = org(OrganizationStatus.APPROVED);
        UUID other = org(OrganizationStatus.APPROVED);
        member(organizer, approved, OrganizationRole.ORGANIZER);
        member(scanner, other, OrganizationRole.CHECK_IN_STAFF);

        assertThat(ids(mine(organizer))).containsExactly(approved.toString());
        assertThat(ids(mine(scanner))).containsExactly(other.toString());
    }

    @Test
    void aMemberOfSeveralOrganizationsSeesAllOfThemOnce_oldestFirst() throws Exception {
        User user = user(Role.CUSTOMER);
        UUID first = org(OrganizationStatus.APPROVED);
        UUID second = org(OrganizationStatus.SUSPENDED);
        UUID third = org(OrganizationStatus.APPROVED);
        member(user, third, OrganizationRole.ORGANIZER);
        member(user, first, OrganizationRole.OWNER);
        member(user, second, OrganizationRole.CHECK_IN_STAFF);

        assertThat(ids(mine(user))).containsExactly(first.toString(), second.toString(), third.toString());
    }

    @Test
    void anApplicantSeesTheirPendingAndRejectedApplications() throws Exception {
        User applicant = user(Role.CUSTOMER);
        String body = "{\"name\":\"Applicant Org %s\",\"documents\":[{\"type\":\"business_permit\",\"url\":\"https://docs/permit.pdf\"}]}";
        UUID pending = applied(applicant, body.formatted(UUID.randomUUID()));
        UUID rejected = applied(applicant, body.formatted(UUID.randomUUID()));
        mockMvc.perform(post("/api/v1/organizations/{id}/reject", rejected).header("Authorization", token(user(Role.ADMIN)))
                        .contentType("application/json").content("{\"reason\":\"Missing documents\"}"))
                .andExpect(status().isOk());

        JsonNode list = mine(applicant);

        assertThat(ids(list)).containsExactly(pending.toString(), rejected.toString());
        assertThat(list.get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(list.get(1).get("status").asText()).isEqualTo("REJECTED");
        assertThat(list.get(1).get("rejectionReason").asText()).isEqualTo("Missing documents");
    }

    private UUID applied(User applicant, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations").header("Authorization", token(applicant))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        orgIds.add(id);
        return id;
    }

    @Test
    void anApplicantWhoIsAlsoAMemberSeesTheOrganizationOnce() throws Exception {
        User applicant = user(Role.CUSTOMER);
        UUID id = applied(applicant, "{\"name\":\"Both Org %s\",\"documents\":[{\"type\":\"business_permit\",\"url\":\"https://docs/permit.pdf\"}]}"
                .formatted(UUID.randomUUID()));
        member(applicant, id, OrganizationRole.OWNER);

        assertThat(ids(mine(applicant))).containsExactly(id.toString());
    }

    @Test
    void anUnrelatedUserGetsAnEmptyList_notA404() throws Exception {
        org(OrganizationStatus.APPROVED);

        assertThat(mine(user(Role.CUSTOMER))).isEmpty();
    }

    @Test
    void anAdminWhoIsNotAMemberGetsOnlyTheirOwn() throws Exception {
        User admin = user(Role.ADMIN);
        org(OrganizationStatus.APPROVED);
        org(OrganizationStatus.SUSPENDED);
        UUID own = org(OrganizationStatus.APPROVED);
        member(admin, own, OrganizationRole.ORGANIZER);

        assertThat(ids(mine(admin))).containsExactly(own.toString());
    }

    @Test
    void aSoftDeletedOrganizationIsLeftOut() throws Exception {
        User owner = user(Role.CUSTOMER);
        UUID kept = org(OrganizationStatus.APPROVED);
        UUID removed = org(OrganizationStatus.APPROVED);
        member(owner, kept, OrganizationRole.OWNER);
        member(owner, removed, OrganizationRole.OWNER);
        Organization toDelete = organizationRepository.findById(removed).orElseThrow();
        toDelete.markDeleted();
        organizationRepository.save(toDelete);

        assertThat(ids(mine(owner))).containsExactly(kept.toString());
    }

    @Test
    void withoutSigningIn_itIs401() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/mine")).andExpect(status().isUnauthorized());
    }
}
