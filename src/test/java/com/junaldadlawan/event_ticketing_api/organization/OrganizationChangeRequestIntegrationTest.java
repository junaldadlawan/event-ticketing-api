package com.junaldadlawan.event_ticketing_api.organization;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An approved organization is edited through a change request an admin approves; the live organization changes only then. */
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationChangeRequestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();

    private User adminUser;
    private User owner;
    private User teamOrganizer;
    private User plainOrganizer;
    private User staff;
    private User stranger;
    private UUID org;

    @BeforeEach
    void setUp() {
        adminUser = persist(Role.ADMIN);
        owner = persist(Role.CUSTOMER);
        teamOrganizer = persist(Role.CUSTOMER);
        plainOrganizer = persist(Role.CUSTOMER);
        staff = persist(Role.CUSTOMER);
        stranger = persist(Role.CUSTOMER);
        org = organization(OrganizationStatus.APPROVED);
        member(owner, OrganizationRole.OWNER, null);
        member(teamOrganizer, OrganizationRole.ORGANIZER, Set.of(OrganizationPermission.TEAM));
        member(plainOrganizer, OrganizationRole.ORGANIZER, Set.of(OrganizationPermission.EVENTS));
        member(staff, OrganizationRole.CHECK_IN_STAFF, null);
    }

    @AfterEach
    void tearDown() {
        orgIds.forEach(id -> {
            jdbc.update("delete from notifications where related_object_type = 'OrganizationChangeRequest' and related_object_id in "
                    + "(select id from organization_change_requests where organization_id = ?)", id);
            jdbc.update("delete from organization_change_requests where organization_id = ?", id);
            jdbc.update("delete from audit_log_entries where organization_id = ?", id);
        });
        jdbc.update("delete from notifications where user_id = ?", adminUser.getId());
        members.forEach(m -> organizationMemberRepository.findByUserIdAndOrganizationId(m.getUserId(), m.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        orgIds.forEach(organizationRepository::deleteById);
        userIds.forEach(userRepository::deleteById);
    }

    private User persist(Role role) {
        User saved = userRepository.save(User.builder().firstName("Org").lastName("Change").email("orgchg-" + UUID.randomUUID() + "@test.local")
                .passwordHash("x").role(role).build());
        userIds.add(saved.getId());
        return saved;
    }

    private UUID organization(OrganizationStatus status) {
        UUID id = organizationRepository.save(Organization.builder().name("Change Org " + UUID.randomUUID()).status(status).ownerId(owner.getId())
                .documents(List.of()).build()).getId();
        orgIds.add(id);
        return id;
    }

    private void member(User user, OrganizationRole role, Set<OrganizationPermission> permissions) {
        OrganizationMember member = OrganizationMember.builder().userId(user.getId()).organizationId(org).build();
        member.getRoles().add(role);
        if (permissions != null) {
            member.choosePermissions(permissions);
        }
        members.add(organizationMemberRepository.save(member));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private ResultActions patchOrg(User caller, UUID orgId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/organizations/{id}", orgId).header("Authorization", bearer(caller)).contentType("application/json").content(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String orgValue(String column) {
        return jdbc.queryForObject("select " + column + " from organizations where id = ?", String.class, org);
    }

    @Test
    void aRequestStoresOnlyTheChangedFields_theLiveOrganizationDoesNotChange_andAskingAgainReplacesIt() throws Exception {
        String originalName = orgValue("name");
        JsonNode first = json(patchOrg(owner, org, "{\"name\":\"New Name\",\"contactEmail\":\"hello@example.com\",\"address\":\"  \"}")
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requested.name").value("New Name")).andExpect(jsonPath("$.requested.contactEmail").value("hello@example.com"))
                .andExpect(jsonPath("$.requested.address").doesNotExist()));   // a blank address clears nothing that was never set: not a change
        assertThat(orgValue("name")).isEqualTo(originalName);   // the live organization is untouched
        assertThat(orgValue("contact_email")).isNull();

        // the same values again: nothing to change
        patchOrg(owner, org, "{\"description\":\"\"}").andExpect(status().isBadRequest());
        patchOrg(owner, org, "{\"name\":\"  \"}").andExpect(status().isBadRequest());
        patchOrg(owner, org, "{\"contactEmail\":\"not-an-email\"}").andExpect(status().isBadRequest());
        patchOrg(owner, org, "{\"contactPhone\":\"abc\"}").andExpect(status().isBadRequest());

        // a new request replaces the pending one: still one pending, the same row
        JsonNode second = json(patchOrg(teamOrganizer, org, "{\"contactPhone\":\"+63 912 345 6789\"}").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requested.contactPhone").value("+63 912 345 6789")).andExpect(jsonPath("$.requested.name").doesNotExist()));
        assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());
        mockMvc.perform(get("/api/v1/organizations/{id}/change-requests", org).header("Authorization", bearer(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].requestedBy").value(teamOrganizer.getId().toString()));
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where organization_id = ? and action = 'organization.change_requested'", Integer.class, org)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'ORGANIZATION_CHANGE_REQUESTED'", Integer.class, adminUser.getId())).isGreaterThanOrEqualTo(1);
    }

    @Test
    void whoMayAskAndWhoMayLook() throws Exception {
        patchOrg(plainOrganizer, org, "{\"address\":\"Somewhere\"}").andExpect(status().isForbidden());   // an organizer needs TEAM
        patchOrg(staff, org, "{\"address\":\"Somewhere\"}").andExpect(status().isForbidden());
        patchOrg(stranger, org, "{\"address\":\"Somewhere\"}").andExpect(status().isForbidden());
        patchOrg(teamOrganizer, org, "{\"address\":\"Somewhere\"}").andExpect(status().isAccepted());
        UUID pending = organization(OrganizationStatus.PENDING);
        patchOrg(owner, pending, "{\"address\":\"x\"}").andExpect(status().isForbidden());   // only an approved organization

        mockMvc.perform(get("/api/v1/organizations/{id}/change-requests", org).header("Authorization", bearer(stranger))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/organizations/{id}/change-requests", org).header("Authorization", bearer(adminUser))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/admin/organization-change-requests").header("Authorization", bearer(owner))).andExpect(status().isForbidden());
    }

    @Test
    void anAdminSeesCurrentAndRequestedSideBySide_approvingAppliesIt_rejectingNeedsAReason() throws Exception {
        patchOrg(owner, org, "{\"name\":\"Renamed Org\",\"description\":\"We run gigs\"}").andExpect(status().isAccepted());
        JsonNode listed = json(mockMvc.perform(get("/api/v1/admin/organization-change-requests").param("status", "PENDING").param("size", "100")
                .header("Authorization", bearer(adminUser))).andExpect(status().isOk()));
        JsonNode mine = null;
        for (JsonNode item : listed.get("content")) {
            if (item.get("organizationId").asText().equals(org.toString())) {
                mine = item;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("requested").get("name").asText()).isEqualTo("Renamed Org");
        assertThat(mine.get("current").get("name").asText()).startsWith("Change Org ");   // the live value next to the requested one
        String id = mine.get("id").asText();

        mockMvc.perform(post("/api/v1/admin/organization-change-requests/{id}/reject", id).header("Authorization", bearer(adminUser))
                .contentType("application/json").content("{\"reason\":\"  \"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/organization-change-requests/{id}/approve", id).header("Authorization", bearer(owner))).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/organization-change-requests/{id}/approve", id).header("Authorization", bearer(adminUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.decidedBy").value(adminUser.getId().toString()));
        assertThat(orgValue("name")).isEqualTo("Renamed Org");
        assertThat(orgValue("description")).isEqualTo("We run gigs");
        mockMvc.perform(post("/api/v1/admin/organization-change-requests/{id}/approve", id).header("Authorization", bearer(adminUser))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'ORGANIZATION_CHANGE_DECIDED'", Integer.class, owner.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where organization_id = ? and action = 'organization.change_approved'", Integer.class, org)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/organizations/{id}", org).header("Authorization", bearer(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Org")).andExpect(jsonPath("$.description").value("We run gigs"))
                .andExpect(jsonPath("$.contactEmail").doesNotExist());

        // a second request, rejected with a reason: the organization keeps what it has, and the history lists newest first
        JsonNode second = json(patchOrg(owner, org, "{\"address\":\"1 Main St\"}").andExpect(status().isAccepted()));
        mockMvc.perform(post("/api/v1/admin/organization-change-requests/{id}/reject", second.get("id").asText()).header("Authorization", bearer(adminUser))
                .contentType("application/json").content("{\"reason\":\"Address could not be verified\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED")).andExpect(jsonPath("$.rejectionReason").value("Address could not be verified"));
        assertThat(orgValue("address")).isNull();
        mockMvc.perform(get("/api/v1/organizations/{id}/change-requests", org).header("Authorization", bearer(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].status").value("REJECTED")).andExpect(jsonPath("$[1].status").value("APPROVED"));
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where organization_id = ? and action = 'organization.change_rejected'", Integer.class, org)).isEqualTo(1);
    }

    @Test
    void onlyTheRequesterWithdrawsAPendingRequest() throws Exception {
        JsonNode request = json(patchOrg(teamOrganizer, org, "{\"address\":\"Old plan\"}").andExpect(status().isAccepted()));
        String id = request.get("id").asText();

        mockMvc.perform(delete("/api/v1/organizations/{org}/change-requests/{id}", org, id).header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/organizations/{org}/change-requests/{id}", org, id).header("Authorization", bearer(teamOrganizer))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/organizations/{id}/change-requests", org).header("Authorization", bearer(owner))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete("/api/v1/organizations/{org}/change-requests/{id}", org, id).header("Authorization", bearer(teamOrganizer))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/organization-change-requests/{id}/approve", id).header("Authorization", bearer(adminUser))).andExpect(status().isNotFound());

        // a decided request cannot be withdrawn
        JsonNode decided = json(patchOrg(owner, org, "{\"address\":\"New plan\"}").andExpect(status().isAccepted()));
        mockMvc.perform(post("/api/v1/admin/organization-change-requests/{id}/approve", decided.get("id").asText()).header("Authorization", bearer(adminUser))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/organizations/{org}/change-requests/{id}", org, decided.get("id").asText()).header("Authorization", bearer(owner))).andExpect(status().isConflict());
    }

    @Test
    void anApplicationCarriesTheOptionalDetails_withTheSameValidation_andTheAdminSeesThemInTheReview() throws Exception {
        String applicant = bearer(persist(Role.CUSTOMER));
        String docs = "\"documents\":[{\"type\":\"business_permit\",\"url\":\"https://docs/permit.pdf\"}]";

        mockMvc.perform(post("/api/v1/organizations").header("Authorization", applicant).contentType("application/json")
                .content("{\"name\":\"Bad Mail Org\"," + docs + ",\"contactEmail\":\"nope\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", applicant).contentType("application/json")
                .content("{\"name\":\"Bad Phone Org\"," + docs + ",\"contactPhone\":\"call me\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", applicant).contentType("application/json")
                .content("{\"name\":\"Html Org\"," + docs + ",\"description\":\"<script>x</script>\"}")).andExpect(status().isBadRequest());

        String name = "Detailed Org " + UUID.randomUUID();
        JsonNode created = json(mockMvc.perform(post("/api/v1/organizations").header("Authorization", applicant).contentType("application/json")
                .content("{\"name\":\"" + name + "\"," + docs + ",\"contactEmail\":\"hello@example.com\",\"contactPhone\":\"+63 912 345 6789\","
                        + "\"address\":\"1 Main St\",\"description\":\" We run gigs \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.contactEmail").value("hello@example.com"))
                .andExpect(jsonPath("$.description").value("We run gigs")));
        UUID created_id = UUID.fromString(created.get("id").asText());
        orgIds.add(created_id);

        mockMvc.perform(get("/api/v1/organizations/{id}", created_id).header("Authorization", bearer(adminUser))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contactPhone").value("+63 912 345 6789")).andExpect(jsonPath("$.address").value("1 Main St"));
        mockMvc.perform(get("/api/v1/organizations").param("status", "pending").param("size", "100").header("Authorization", bearer(adminUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + created_id + "')].contactEmail").value("hello@example.com"));
    }

    @Test
    void anAdminAsksForARevision_theApplicantEditsThePendingApplicationDirectly_andTheFlagClears() throws Exception {
        User applicantUser = persist(Role.CUSTOMER);
        String applicant = bearer(applicantUser);
        String docs = "\"documents\":[{\"type\":\"business_permit\",\"url\":\"https://docs/old.pdf\"}]";
        JsonNode created = json(mockMvc.perform(post("/api/v1/organizations").header("Authorization", applicant).contentType("application/json")
                .content("{\"name\":\"Revise Me " + UUID.randomUUID() + "\"," + docs + "}")).andExpect(status().isCreated()).andExpect(jsonPath("$.revisionRequested").value(false)));
        UUID id = UUID.fromString(created.get("id").asText());
        orgIds.add(id);

        // only an admin, only with a message, only for a pending application
        mockMvc.perform(post("/api/v1/organizations/{id}/request-revision", id).header("Authorization", applicant).contentType("application/json")
                .content("{\"message\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/organizations/{id}/request-revision", id).header("Authorization", bearer(adminUser)).contentType("application/json")
                .content("{\"message\":\"  \"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/organizations/{id}/request-revision", org).header("Authorization", bearer(adminUser)).contentType("application/json")
                .content("{\"message\":\"x\"}")).andExpect(status().isConflict());   // the fixture organization is approved

        mockMvc.perform(post("/api/v1/organizations/{id}/request-revision", id).header("Authorization", bearer(adminUser)).contentType("application/json")
                .content("{\"message\":\"The permit is blurry, please upload a clearer copy\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.revisionRequested").value(true))
                .andExpect(jsonPath("$.revisionMessage").value("The permit is blurry, please upload a clearer copy")).andExpect(jsonPath("$.revisionRequestedAt").exists());
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'ORGANIZATION_REVISION_REQUESTED'", Integer.class, applicantUser.getId())).isEqualTo(1);

        // nobody but the applicant edits it (not even an admin); an empty body changes nothing
        patchOrg(stranger, id, "{\"description\":\"x\"}").andExpect(status().isForbidden());
        patchOrg(adminUser, id, "{\"description\":\"x\"}").andExpect(status().isForbidden());
        patchOrg(applicantUser, id, "{\"description\":\"\"}").andExpect(status().isBadRequest());
        patchOrg(applicantUser, id, "{\"documents\":[]}").andExpect(status().isBadRequest());

        // the applicant edits name, details and documents directly: 200, the application changes at once, the flag clears, the admins are told
        patchOrg(applicantUser, id, "{\"name\":\"Revised Org\",\"address\":\"1 Main St\",\"documents\":[{\"type\":\"business_permit\",\"url\":\"https://docs/new.pdf\"}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Revised Org")).andExpect(jsonPath("$.address").value("1 Main St"))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.revisionRequested").value(false))
                .andExpect(jsonPath("$.revisionMessage").doesNotExist()).andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.documents[0].url").value("https://docs/new.pdf"));
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'ORGANIZATION_APPLICATION_REVISED'", Integer.class, adminUser.getId())).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log_entries where organization_id = ? and action in ('organization.revision_requested', 'organization.application_revised')",
                Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from organization_change_requests where organization_id = ?", Integer.class, id)).isZero();   // no change request for a pending application

        // once approved the documents are no longer editable through PATCH
        mockMvc.perform(post("/api/v1/organizations/{id}/approve", id).header("Authorization", bearer(adminUser))).andExpect(status().isOk());
        patchOrg(applicantUser, id, "{\"documents\":[{\"type\":\"business_permit\",\"url\":\"https://docs/again.pdf\"}]}").andExpect(status().isAccepted());
        jdbc.update("delete from organization_members where organization_id = ?", id);
    }

    // ---- documents through the change request ----

    private List<String> documentUrls() {
        return jdbc.queryForList("select url from organization_documents where organization_id = ? order by sort_order", String.class, org);
    }

    private static String doc(String type, String url) {
        return "{\"type\":\"" + type + "\",\"url\":\"" + url + "\"}";
    }

    private void approve(String requestId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/organization-change-requests/{id}/approve", requestId).header("Authorization", bearer(adminUser))).andExpect(status().isOk());
    }

    @Test
    void anOrganizationWithNoDocumentsAddsItsFirstOne_theRequestShowsNewNextToCurrent_andApprovalAppliesIt() throws Exception {
        assertThat(documentUrls()).isEmpty();
        JsonNode request = json(patchOrg(owner, org, "{\"documents\":[" + doc("business_permit", "https://files.example.com/permit.pdf") + "]}")
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.requested.documents.length()").value(1))
                .andExpect(jsonPath("$.requested.documents[0].type").value("business_permit"))
                .andExpect(jsonPath("$.requested.documents[0].url").value("https://files.example.com/permit.pdf"))
                .andExpect(jsonPath("$.current.documents.length()").value(0)));
        assertThat(documentUrls()).isEmpty();   // nothing changes until an admin approves

        mockMvc.perform(get("/api/v1/organizations/{id}/change-requests", org).header("Authorization", bearer(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requested.documents[0].url").value("https://files.example.com/permit.pdf"));
        mockMvc.perform(get("/api/v1/admin/organization-change-requests").param("status", "PENDING").param("size", "100").header("Authorization", bearer(adminUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id=='" + request.get("id").asText() + "')].requested.documents[0].type").value("business_permit"));

        approve(request.get("id").asText());
        assertThat(documentUrls()).containsExactly("https://files.example.com/permit.pdf");
        assertThat(jdbc.queryForObject("select details::text from audit_log_entries where organization_id = ? and action = 'organization.change_approved'", String.class, org))
                .contains("https://files.example.com/permit.pdf");
    }

    @Test
    void approvingReplacesTheDocuments_keepsTheReplacedOnesInTheAudit_togetherWithDetails() throws Exception {
        jdbc.update("insert into organization_documents (organization_id, type, url, sort_order) values (?, 'business_permit', 'https://files.example.com/old.pdf', 0)", org);
        JsonNode request = json(patchOrg(owner, org, "{\"address\":\"1 Main St\",\"documents\":[" + doc("business_permit", "https://files.example.com/new.pdf") + ","
                + doc("tax_id", "https://files.example.com/tax.pdf") + "]}").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requested.address").value("1 Main St")).andExpect(jsonPath("$.current.documents[0].url").value("https://files.example.com/old.pdf")));
        assertThat(documentUrls()).containsExactly("https://files.example.com/old.pdf");   // the old file stays readable until approval

        approve(request.get("id").asText());
        assertThat(documentUrls()).containsExactly("https://files.example.com/new.pdf", "https://files.example.com/tax.pdf");
        assertThat(orgValue("address")).isEqualTo("1 Main St");
        // the replaced file's record is in the audit trail (the "from" of the change)
        assertThat(jdbc.queryForObject("select details::text from audit_log_entries where organization_id = ? and action = 'organization.change_approved'", String.class, org))
                .contains("old.pdf").contains("new.pdf").contains("tax.pdf");
        assertThat(jdbc.queryForObject("select details::text from audit_log_entries where organization_id = ? and action = 'organization.change_requested'", String.class, org))
                .contains("new.pdf");
    }

    @Test
    void rejectingOrWithdrawingKeepsTheOldDocuments() throws Exception {
        jdbc.update("insert into organization_documents (organization_id, type, url, sort_order) values (?, 'business_permit', 'https://files.example.com/old.pdf', 0)", org);
        JsonNode first = json(patchOrg(owner, org, "{\"documents\":[" + doc("business_permit", "https://files.example.com/new.pdf") + "]}").andExpect(status().isAccepted()));
        mockMvc.perform(post("/api/v1/admin/organization-change-requests/{id}/reject", first.get("id").asText()).header("Authorization", bearer(adminUser))
                .contentType("application/json").content("{\"reason\":\"Unreadable scan\"}")).andExpect(status().isOk());
        assertThat(documentUrls()).containsExactly("https://files.example.com/old.pdf");

        JsonNode second = json(patchOrg(owner, org, "{\"documents\":[" + doc("business_permit", "https://files.example.com/other.pdf") + "]}").andExpect(status().isAccepted()));
        mockMvc.perform(delete("/api/v1/organizations/{org}/change-requests/{id}", org, second.get("id").asText()).header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        assertThat(documentUrls()).containsExactly("https://files.example.com/old.pdf");
    }

    @Test
    void documentsAreValidated_andTheSameDocumentsAreNotAChange() throws Exception {
        patchOrg(owner, org, "{\"documents\":[]}").andExpect(status().isBadRequest());
        patchOrg(owner, org, "{\"documents\":[" + doc("business_permit", "not a url") + "]}").andExpect(status().isBadRequest());
        patchOrg(owner, org, "{\"documents\":[" + doc("business_permit", "ftp://files.example.com/x.pdf") + "]}").andExpect(status().isBadRequest());
        patchOrg(owner, org, "{\"documents\":[" + doc("", "https://files.example.com/x.pdf") + "]}").andExpect(status().isBadRequest());
        patchOrg(owner, org, "{\"documents\":[" + doc("<b>permit</b>", "https://files.example.com/x.pdf") + "]}").andExpect(status().isBadRequest());
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            many.append(i == 0 ? "" : ",").append(doc("t", "https://files.example.com/" + i + ".pdf"));
        }
        patchOrg(owner, org, "{\"documents\":[" + many + "]}").andExpect(status().isBadRequest());

        // the documents it already has are not a change; with another field only that one is requested
        jdbc.update("insert into organization_documents (organization_id, type, url, sort_order) values (?, 'business_permit', 'https://files.example.com/old.pdf', 0)", org);
        patchOrg(owner, org, "{\"documents\":[" + doc("business_permit", "https://files.example.com/old.pdf") + "]}").andExpect(status().isBadRequest());
        patchOrg(owner, org, "{\"address\":\"2 Side St\",\"documents\":[" + doc("business_permit", "https://files.example.com/old.pdf") + "]}")
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.requested.address").value("2 Side St")).andExpect(jsonPath("$.requested.documents").doesNotExist());
    }
}
