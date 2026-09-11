package com.junaldadlawan.event_ticketing_api.organization.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.service.OrganizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link OrganizationController}'s own behavior (request
 * validation, response mapping, status codes) — mirrors
 * {@code UserControllerTest}. Security-filter enforcement (401/403) is
 * exercised separately in {@code OrganizationSecurityIntegrationTest} and
 * {@code EventOrganizationAccessIntegrationTest}.
 */
@WebMvcTest(OrganizationController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationService organizationService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private Organization organization(OrganizationStatus status) {
        Organization organization = Organization.builder()
                .id(UUID.randomUUID())
                .name("Acme Events")
                .status(status)
                .documents(List.of())
                .build();
        ReflectionTestUtils.setField(organization, "createdBy", UUID.randomUUID().toString());
        return organization;
    }

    @Test
    void apply_validRequest_returns201() throws Exception {
        Organization saved = organization(OrganizationStatus.PENDING);
        when(organizationService.apply(any())).thenReturn(saved);

        mockMvc.perform(post("/api/v1/organizations")
                        .contentType("application/json")
                        .content("""
                                {"name":"Acme Events","documents":[{"type":"business_permit","url":"https://docs/permit.pdf"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Events"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.ownerId").doesNotExist());
    }

    /**
     * Regression test for the GlobalExceptionHandler bug the code-reviewer
     * flagged: before the MethodArgumentNotValidException handler was added,
     * a validation failure fell through to Spring's default error handling,
     * which re-entered SecurityConfig's filter chain with no SecurityContext
     * and got misreported as 401 by the custom authenticationEntryPoint.
     * Must be 400, not 401.
     */
    @Test
    void apply_blankName_returns400NotUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType("application/json")
                        .content("""
                                {"name":"","documents":[{"type":"business_permit","url":"https://docs/permit.pdf"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void apply_emptyDocuments_returns400NotUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType("application/json")
                        .content("""
                                {"name":"Acme Events","documents":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void list_returnsMappedList() throws Exception {
        when(organizationService.list("pending")).thenReturn(List.of(organization(OrganizationStatus.PENDING)));

        mockMvc.perform(get("/api/v1/organizations").param("status", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void get_existingOrganization_returns200() throws Exception {
        Organization organization = organization(OrganizationStatus.APPROVED);
        when(organizationService.get(organization.getId())).thenReturn(organization);

        mockMvc.perform(get("/api/v1/organizations/{orgId}", organization.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(organizationService.get(orgId)).thenThrow(new ResourceNotFoundException("Organization " + orgId + " not found"));

        mockMvc.perform(get("/api/v1/organizations/{orgId}", orgId))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_forbidden_returns403() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(organizationService.get(orgId)).thenThrow(new ForbiddenException("Not authorized to view this organization"));

        mockMvc.perform(get("/api/v1/organizations/{orgId}", orgId))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_validRequest_returns200() throws Exception {
        Organization updated = organization(OrganizationStatus.APPROVED);
        updated.setName("New Name");
        when(organizationService.update(eq(updated.getId()), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/organizations/{orgId}", updated.getId())
                        .contentType("application/json")
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void update_blankName_returns400() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/organizations/{orgId}", orgId)
                        .contentType("application/json")
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approve_pendingOrganization_returns200() throws Exception {
        Organization approved = organization(OrganizationStatus.APPROVED);
        when(organizationService.approve(approved.getId())).thenReturn(approved);

        mockMvc.perform(post("/api/v1/organizations/{orgId}/approve", approved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approve_notPending_returns409() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(organizationService.approve(orgId)).thenThrow(new ConflictException("Organization application is not pending"));

        mockMvc.perform(post("/api/v1/organizations/{orgId}/approve", orgId))
                .andExpect(status().isConflict());
    }

    @Test
    void reject_pendingOrganization_returns200() throws Exception {
        Organization rejected = organization(OrganizationStatus.REJECTED);
        rejected.setRejectionReason("Missing permit");
        when(organizationService.reject(eq(rejected.getId()), any())).thenReturn(rejected);

        mockMvc.perform(post("/api/v1/organizations/{orgId}/reject", rejected.getId())
                        .contentType("application/json")
                        .content("""
                                {"reason":"Missing permit"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Missing permit"));
    }

    @Test
    void reject_notPending_returns409() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(organizationService.reject(eq(orgId), any())).thenThrow(new ConflictException("Organization application is not pending"));

        mockMvc.perform(post("/api/v1/organizations/{orgId}/reject", orgId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listMembers_returnsMappedList() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrganizationMember member = OrganizationMember.builder()
                .userId(userId)
                .organizationId(orgId)
                .build();
        member.getRoles().add(OrganizationRole.OWNER);
        when(organizationService.listMembers(orgId)).thenReturn(List.of(member));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/members", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$[0].roles[0]").value("OWNER"));
    }

    @Test
    void assignMember_validRequest_returns201() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        OrganizationMember member = OrganizationMember.builder()
                .userId(targetUserId)
                .organizationId(orgId)
                .roles(Set.of(OrganizationRole.ORGANIZER))
                .build();
        when(organizationService.assignMember(eq(orgId), any())).thenReturn(member);

        mockMvc.perform(post("/api/v1/organizations/{orgId}/members", orgId)
                        .contentType("application/json")
                        .content("""
                                {"userId":"%s","role":"ORGANIZER"}
                                """.formatted(targetUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.roles[0]").value("ORGANIZER"));
    }

    @Test
    void assignMember_missingUserId_returns400() throws Exception {
        UUID orgId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/organizations/{orgId}/members", orgId)
                        .contentType("application/json")
                        .content("""
                                {"role":"ORGANIZER"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignMember_ownerRoleRequested_returns403() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(organizationService.assignMember(eq(orgId), any()))
                .thenThrow(new ForbiddenException("The owner role cannot be granted through this endpoint"));

        mockMvc.perform(post("/api/v1/organizations/{orgId}/members", orgId)
                        .contentType("application/json")
                        .content("""
                                {"userId":"%s","role":"OWNER"}
                                """.formatted(targetUserId)))
                .andExpect(status().isForbidden());
    }
}
