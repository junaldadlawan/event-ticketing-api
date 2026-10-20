package com.junaldadlawan.event_ticketing_api.auditlog;

import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;
import com.junaldadlawan.event_ticketing_api.auditlog.repository.AuditLogEntryRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BR-ADMIN-007: GET /activity - each tab's log shows only its actions, only its organization's, only to its readers. */
@SpringBootTest(properties = "payouts.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")   // the test-only key PayMongoPayoutIntegrationTest uses
@AutoConfigureMockMvc
class ActivityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private AuditLogEntryRepository auditLogEntryRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();
    private final List<UUID> entryIds = new ArrayList<>();

    private UUID orgA;
    private UUID orgB;
    private String owner;
    private String organizer;
    private String stranger;
    private String admin;

    @BeforeEach
    void setUp() {
        User ownerUser = user(Role.CUSTOMER);
        User organizerUser = user(Role.CUSTOMER);
        orgA = persistOrganization(ownerUser.getId());
        orgB = persistOrganization(UUID.randomUUID());
        grant(ownerUser.getId(), orgA, OrganizationRole.OWNER);
        grant(organizerUser.getId(), orgA, OrganizationRole.ORGANIZER);
        owner = bearer(ownerUser);
        organizer = bearer(organizerUser);
        stranger = bearer(user(Role.CUSTOMER));
        admin = bearer(user(Role.ADMIN));

        // oldest first: the log must come back newest first
        UUID eventA = UUID.randomUUID();
        seed(orgA, eventA, "event.updated");
        seed(orgA, eventA, "ticket_type.created");
        seed(orgA, null, "organization_member.assigned");
        seed(orgA, null, "organization.updated");
        seed(orgA, null, "payout.generated");
        seed(orgA, null, "payout_card.added");
        seed(orgA, null, "payout_account.set");
        seed(orgA, eventA, "refund.issued");
        // another organization's entries must never leak into orgA's tabs
        seed(orgB, UUID.randomUUID(), "event.updated");
        seed(orgB, null, "payout.generated");
        seed(orgB, null, "organization_member.assigned");
        seed(null, null, "post.created");
    }

    @AfterEach
    void tearDown() {
        entryIds.forEach(id -> auditLogEntryRepository.findById(id).ifPresent(auditLogEntryRepository::delete));
        orgIds.forEach(id -> {
            jdbc.update("delete from audit_log_entries where organization_id = ?", id);
            jdbc.update("delete from payout_accounts where organization_id = ?", id);
        });
        members.forEach(m -> organizationMemberRepository.findByUserIdAndOrganizationId(m.getUserId(), m.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        orgIds.forEach(id -> organizationRepository.findById(id).ifPresent(organizationRepository::delete));
    }

    @Test
    void eachOrganizationTab_showsOnlyItsActions_ofThatOrganization_newestFirst() throws Exception {
        assertThat(actions(owner, "EVENTS", orgA)).containsExactly("refund.issued", "ticket_type.created", "event.updated");
        assertThat(actions(owner, "TEAM", orgA)).containsExactly("organization.updated", "organization_member.assigned");
        assertThat(actions(owner, "FINANCE", orgA)).containsExactly("refund.issued", "payout.generated");
        assertThat(actions(owner, "SETTINGS", orgA)).containsExactly("payout_account.set", "payout_card.added");
        // and paging keeps the order
        assertThat(readEntries(get("/api/v1/activity").param("area", "EVENTS").param("organizationId", orgA.toString())
                        .param("size", "2").param("page", "1").header("Authorization", owner)))
                .extracting(entry -> entry.get("action").asText()).containsExactly("event.updated");
    }

    @Test
    void whoMayReadAnOrganizationsTabs() throws Exception {
        // organizer: everything but finance
        assertThat(actions(organizer, "EVENTS", orgA)).hasSize(3);
        assertThat(actions(organizer, "TEAM", orgA)).hasSize(2);
        assertThat(actions(organizer, "SETTINGS", orgA)).hasSize(2);
        mockMvc.perform(activity(organizer, "FINANCE", orgA)).andExpect(status().isForbidden());
        // not a member
        mockMvc.perform(activity(stranger, "EVENTS", orgA)).andExpect(status().isForbidden());
        mockMvc.perform(activity(owner, "EVENTS", orgB)).andExpect(status().isForbidden());
        // an admin reads any organization's tabs
        assertThat(actions(admin, "FINANCE", orgB)).containsExactly("payout.generated");
        // unknown organization, an area with no organization tab, an unknown area
        mockMvc.perform(activity(admin, "EVENTS", UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(activity(owner, "POSTS", orgA)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/activity").param("area", "NOPE").header("Authorization", owner)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/activity").param("area", "EVENTS").param("organizationId", orgA.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void platformTabs_areForAdminsOnly_andShowOnlyTheirActions() throws Exception {
        mockMvc.perform(activity(owner, "POSTS", null)).andExpect(status().isForbidden());
        mockMvc.perform(activity(admin, "TEAM", null)).andExpect(status().isBadRequest());

        assertThat(actions(admin, "POSTS", null)).isNotEmpty().allMatch(action -> action.startsWith("post."));
        assertThat(actions(admin, "FINANCE", null)).allMatch(action -> action.startsWith("payout.")
                || action.startsWith("platform_fee.") || action.startsWith("gateway_fee."));
        assertThat(actions(admin, "EVENTS", null)).contains("event.updated", "ticket_type.created", "refund.issued");
    }

    @Test
    void aPayoutAccountEntry_showsTheBankAndLast4_neverTheAccountNumber() throws Exception {
        mockMvc.perform(put("/api/v1/organizations/{id}/payout-account", orgA).header("Authorization", admin)
                        .contentType("application/json")
                        .content("{\"bankCode\":\"BNORPHMM\",\"bankName\":\"BDO\",\"accountName\":\"Activity Test\",\"accountNumber\":\"1234567890\"}"))
                .andExpect(status().isOk());

        JsonNode entry = readEntries(activity(owner, "SETTINGS", orgA)).get(0);
        assertThat(entry.get("action").asText()).isEqualTo("payout_account.set");
        assertThat(entry.get("details").get("bank").asText()).isEqualTo("BDO");
        assertThat(entry.get("details").get("last4").asText()).isEqualTo("7890");
        assertThat(entry.get("details").toString()).doesNotContain("1234567890").doesNotContain("accountNumber");
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder activity(String caller, String area, UUID organizationId) {
        var request = get("/api/v1/activity").param("area", area).param("size", "100").header("Authorization", caller);
        return organizationId == null ? request : request.param("organizationId", organizationId.toString());
    }

    private List<String> actions(String caller, String area, UUID organizationId) throws Exception {
        List<String> actions = new ArrayList<>();
        readEntries(activity(caller, area, organizationId)).forEach(entry -> actions.add(entry.get("action").asText()));
        return actions;
    }

    private List<JsonNode> readEntries(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        ResultActions result = mockMvc.perform(request).andExpect(status().isOk());
        List<JsonNode> entries = new ArrayList<>();
        objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("content").forEach(entries::add);
        return entries;
    }

    private void seed(UUID organizationId, UUID eventId, String action) {
        entryIds.add(auditLogEntryRepository.saveAndFlush(AuditLogEntry.builder()
                .action(action).targetType("Test").targetId(UUID.randomUUID())
                .organizationId(organizationId).eventId(eventId).build()).getId());
    }

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email("activity-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization saved = organizationRepository.save(Organization.builder()
                .name("Activity Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED)
                .ownerId(ownerId)
                .documents(List.of())
                .build());
        orgIds.add(saved.getId());
        return saved.getId();
    }

    private void grant(UUID userId, UUID organizationId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(organizationId).build();
        member.getRoles().add(role);
        members.add(organizationMemberRepository.save(member));
    }
}
