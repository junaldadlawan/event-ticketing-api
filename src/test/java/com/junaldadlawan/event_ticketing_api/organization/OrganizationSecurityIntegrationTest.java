package com.junaldadlawan.event_ticketing_api.organization;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
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
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real SecurityConfig/JwtAuthenticationFilter wiring end to
 * end for the organization module — mirrors {@code UserSecurityIntegrationTest}.
 * Uses in-memory (unpersisted) {@link User} objects to sign real tokens via
 * the real {@link JwtService} bean: the filter never looks the subject up in
 * the DB, and {@code organizations}/{@code organization_members} carry no FK
 * to {@code users}, so no user row needs to be persisted for these flows.
 * <p>
 * Covers the golden path from the plan's Verification section: BR-ORG-001–005,
 * BR-AUTH-005/007/008, UC-ORG-01, UC-OWNER-01/02, UC-ADMIN-01 — plus the
 * case-insensitive {@code ?status=} admin-only filter.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdOrgIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID orgId : createdOrgIds) {
            List<OrganizationMember> members = organizationMemberRepository.findByOrganizationId(orgId);
            organizationMemberRepository.deleteAll(members);
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

    private UUID applyForOrganization(String applicantToken, String orgName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","documents":[{"type":"business_permit","url":"https://docs/permit.pdf"}]}
                                """.formatted(orgName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andReturn();
        UUID orgId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdOrgIds.add(orgId);
        return orgId;
    }

    @Test
    void goldenPath_applyApproveSelfAssignAssignOther_unrelatedUserForbidden() throws Exception {
        User applicant = inMemoryUser(Role.CUSTOMER);
        String applicantToken = jwtService.generateAccessToken(applicant);
        User admin = inMemoryUser(Role.ADMIN);
        String adminToken = jwtService.generateAccessToken(admin);
        User memberB = inMemoryUser(Role.CUSTOMER);
        User strangerC = inMemoryUser(Role.CUSTOMER);
        String strangerToken = jwtService.generateAccessToken(strangerC);

        // Step 1 (UC-ORG-01 / BR-ORG-001/002): applicant applies, org is PENDING with no owner.
        UUID orgId = applyForOrganization(applicantToken, "Golden Path Org " + UUID.randomUUID());

        // Step 2 (UC-ADMIN-01 / BR-ORG-003/004): admin approves; applicant becomes owner.
        mockMvc.perform(post("/api/v1/organizations/{orgId}/approve", orgId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.ownerId").value(applicant.getId().toString()));

        // Step 3: applicant is now an OWNER member of the org.
        mockMvc.perform(get("/api/v1/organizations/{orgId}/members", orgId)
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId=='" + applicant.getId() + "')].roles[0]", contains("OWNER")));

        // Step 4 (UC-OWNER-02 / BR-AUTH-007/008): applicant self-assigns ORGANIZER on top of OWNER.
        mockMvc.perform(post("/api/v1/organizations/{orgId}/members", orgId)
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType("application/json")
                        .content("""
                                {"userId":"%s","role":"ORGANIZER"}
                                """.formatted(applicant.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("OWNER", "ORGANIZER")));

        // Step 5 (UC-OWNER-01 / BR-AUTH-005/006): owner assigns a second user CHECK_IN_STAFF.
        mockMvc.perform(post("/api/v1/organizations/{orgId}/members", orgId)
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType("application/json")
                        .content("""
                                {"userId":"%s","role":"CHECK_IN_STAFF"}
                                """.formatted(memberB.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("CHECK_IN_STAFF"));

        // Step 6 (UC-OWNER-01 exception flow): a third, unrelated user has no role in
        // this org and is forbidden from assigning anyone.
        mockMvc.perform(post("/api/v1/organizations/{orgId}/members", orgId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content("""
                                {"userId":"%s","role":"CHECK_IN_STAFF"}
                                """.formatted(strangerC.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminList_caseInsensitiveStatusFilter_nonAdminForbidden() throws Exception {
        User applicant = inMemoryUser(Role.CUSTOMER);
        String applicantToken = jwtService.generateAccessToken(applicant);
        User admin = inMemoryUser(Role.ADMIN);
        String adminToken = jwtService.generateAccessToken(admin);

        UUID orgId = applyForOrganization(applicantToken, "Case Insensitive Org " + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/organizations").param("status", "pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + orgId + "')]").exists());

        mockMvc.perform(get("/api/v1/organizations").param("status", "PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + orgId + "')]").exists());

        mockMvc.perform(get("/api/v1/organizations").param("status", "pending")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isForbidden());
    }
}
