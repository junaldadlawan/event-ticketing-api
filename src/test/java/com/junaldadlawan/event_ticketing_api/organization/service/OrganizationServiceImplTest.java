package com.junaldadlawan.event_ticketing_api.organization.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.dto.DocumentDto;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationCreateRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationMemberAssignRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationRejectRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationUpdateRequest;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link OrganizationServiceImpl}, mirroring
 * {@code UserServiceImplTest}'s style (no Spring context). Covers
 * BR-ORG-001–005, BR-AUTH-005/007/008/009, UC-ORG-01, UC-OWNER-01/02,
 * UC-ADMIN-01.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    private OrganizationServiceImpl organizationService;

    private UUID orgId;
    private UUID applicantId;

    @BeforeEach
    void setUp() {
        organizationService = new OrganizationServiceImpl(organizationRepository, organizationMemberRepository, accessGuard);
        orgId = UUID.randomUUID();
        applicantId = UUID.randomUUID();
    }

    private Organization pendingOrg() {
        Organization organization = Organization.builder()
                .id(orgId)
                .name("Acme Events")
                .status(OrganizationStatus.PENDING)
                .documents(List.of())
                .build();
        ReflectionTestUtils.setField(organization, "createdBy", applicantId.toString());
        return organization;
    }

    private Organization approvedOrg() {
        Organization organization = pendingOrg();
        organization.setStatus(OrganizationStatus.APPROVED);
        organization.setOwnerId(applicantId);
        return organization;
    }

    // ---- apply() : BR-ORG-001/002, UC-ORG-01 ----

    @Test
    void apply_createsPendingOrganizationWithNoOwner() {
        OrganizationCreateRequest request = new OrganizationCreateRequest(
                "Acme Events", List.of(new DocumentDto("business_permit", "https://docs/permit.pdf")));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Organization saved = organizationService.apply(request);

        assertThat(saved.getStatus()).isEqualTo(OrganizationStatus.PENDING);
        assertThat(saved.getOwnerId()).isNull();
        assertThat(saved.getName()).isEqualTo("Acme Events");
        assertThat(saved.getDocuments()).hasSize(1);
        assertThat(saved.getDocuments().getFirst().getType()).isEqualTo("business_permit");
    }

    // ---- approve() : BR-ORG-003/004, UC-ADMIN-01 ----

    @Test
    void approve_pendingOrganization_setsApprovedOwnerAndGrantsOwnerRole() {
        Organization organization = pendingOrg();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(organizationMemberRepository.findByUserIdAndOrganizationId(applicantId, orgId)).thenReturn(Optional.empty());
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Organization result = organizationService.approve(orgId);

        assertThat(result.getStatus()).isEqualTo(OrganizationStatus.APPROVED);
        assertThat(result.getOwnerId()).isEqualTo(applicantId);
        verify(accessGuard).requireAdmin();
        ArgumentCaptor<OrganizationMember> memberCaptor = ArgumentCaptor.forClass(OrganizationMember.class);
        verify(organizationMemberRepository).save(memberCaptor.capture());
        OrganizationMember savedMember = memberCaptor.getValue();
        assertThat(savedMember.getUserId()).isEqualTo(applicantId);
        assertThat(savedMember.getOrganizationId()).isEqualTo(orgId);
        assertThat(savedMember.getRoles()).containsExactly(OrganizationRole.OWNER);
    }

    @Test
    void approve_notPending_throwsConflictAndDoesNotSave() {
        Organization organization = approvedOrg();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));

        assertThatThrownBy(() -> organizationService.approve(orgId))
                .isInstanceOf(ConflictException.class);
        verify(organizationRepository, never()).save(any());
        verify(organizationMemberRepository, never()).save(any());
    }

    // ---- reject() : BR-ORG-003/005, UC-ADMIN-01 ----

    @Test
    void reject_pendingOrganization_withReason_setsRejectedAndReason() {
        Organization organization = pendingOrg();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Organization result = organizationService.reject(orgId, new OrganizationRejectRequest("Missing permit"));

        assertThat(result.getStatus()).isEqualTo(OrganizationStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Missing permit");
        verify(accessGuard).requireAdmin();
    }

    @Test
    void reject_pendingOrganization_withoutReason_setsRejectedWithNullReason() {
        Organization organization = pendingOrg();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Organization result = organizationService.reject(orgId, null);

        assertThat(result.getStatus()).isEqualTo(OrganizationStatus.REJECTED);
        assertThat(result.getRejectionReason()).isNull();
    }

    @Test
    void reject_notPending_throwsConflict() {
        Organization organization = approvedOrg();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));

        assertThatThrownBy(() -> organizationService.reject(orgId, new OrganizationRejectRequest("too late")))
                .isInstanceOf(ConflictException.class);
        verify(organizationRepository, never()).save(any());
    }

    // ---- update() : owner/organizer of an APPROVED org only ----

    @Test
    void update_owner_ofApprovedOrg_succeeds() {
        Organization organization = approvedOrg();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(applicantId);
        when(accessGuard.hasRole(applicantId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Organization result = organizationService.update(orgId, new OrganizationUpdateRequest("New Name"));

        assertThat(result.getName()).isEqualTo("New Name");
    }

    @Test
    void update_organizer_ofApprovedOrg_succeeds() {
        Organization organization = approvedOrg();
        UUID organizerId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Organization result = organizationService.update(orgId, new OrganizationUpdateRequest("New Name"));

        assertThat(result.getName()).isEqualTo("New Name");
    }

    @Test
    void update_nonMember_throwsForbidden() {
        Organization organization = approvedOrg();
        UUID strangerId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> organizationService.update(orgId, new OrganizationUpdateRequest("New Name")))
                .isInstanceOf(ForbiddenException.class);
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void update_pendingOrganization_throwsForbidden() {
        Organization organization = pendingOrg();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(applicantId);
        when(accessGuard.hasRole(applicantId, orgId, OrganizationRole.OWNER)).thenReturn(true);

        assertThatThrownBy(() -> organizationService.update(orgId, new OrganizationUpdateRequest("New Name")))
                .isInstanceOf(ForbiddenException.class);
        verify(organizationRepository, never()).save(any());
    }

    // ---- get() : applicant, member, or admin only ----

    @Test
    void get_applicant_succeeds() {
        Organization organization = pendingOrg();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(applicantId);

        Organization result = organizationService.get(orgId);

        assertThat(result).isEqualTo(organization);
    }

    @Test
    void get_member_succeeds() {
        Organization organization = approvedOrg();
        UUID memberId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(memberId);
        when(accessGuard.isMember(memberId, orgId)).thenReturn(true);

        Organization result = organizationService.get(orgId);

        assertThat(result).isEqualTo(organization);
    }

    @Test
    void get_admin_succeeds() {
        Organization organization = approvedOrg();
        UUID adminId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(adminId);
        when(accessGuard.isMember(adminId, orgId)).thenReturn(false);
        when(accessGuard.isAdmin()).thenReturn(true);

        Organization result = organizationService.get(orgId);

        assertThat(result).isEqualTo(organization);
    }

    @Test
    void get_unrelatedUser_throwsForbidden() {
        Organization organization = approvedOrg();
        UUID strangerId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.isMember(strangerId, orgId)).thenReturn(false);
        when(accessGuard.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> organizationService.get(orgId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void get_unknownId_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(organizationRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.get(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- list() : admin-only, BR-AUTH-004 ----

    @Test
    void list_admin_queriesRepository() {
        when(organizationRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        List<Organization> result = organizationService.list("pending");

        assertThat(result).isEmpty();
        verify(accessGuard).requireAdmin();
    }

    @Test
    void list_nonAdmin_throwsForbidden() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> organizationService.list("pending"))
                .isInstanceOf(ForbiddenException.class);
        verify(organizationRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    // ---- listMembers() : BR-AUTH-009 ----

    @Test
    void listMembers_member_succeeds() {
        Organization organization = approvedOrg();
        UUID memberId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(memberId);
        when(accessGuard.isMember(memberId, orgId)).thenReturn(true);
        when(organizationMemberRepository.findByOrganizationId(orgId)).thenReturn(List.of());

        List<OrganizationMember> result = organizationService.listMembers(orgId);

        assertThat(result).isEmpty();
    }

    @Test
    void listMembers_admin_succeeds() {
        Organization organization = approvedOrg();
        UUID adminId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(adminId);
        when(accessGuard.isMember(adminId, orgId)).thenReturn(false);
        when(accessGuard.isAdmin()).thenReturn(true);
        when(organizationMemberRepository.findByOrganizationId(orgId)).thenReturn(List.of());

        List<OrganizationMember> result = organizationService.listMembers(orgId);

        assertThat(result).isEmpty();
    }

    @Test
    void listMembers_nonMemberNonAdmin_throwsForbidden() {
        Organization organization = approvedOrg();
        UUID strangerId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.isMember(strangerId, orgId)).thenReturn(false);
        when(accessGuard.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> organizationService.listMembers(orgId))
                .isInstanceOf(ForbiddenException.class);
        verify(organizationMemberRepository, never()).findByOrganizationId(any());
    }

    // ---- assignMember() : BR-AUTH-005/006/007/008, UC-OWNER-01/02 ----

    @Test
    void assignMember_ownerRoleRequested_alwaysForbidden() {
        Organization organization = approvedOrg();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        OrganizationMemberAssignRequest request = new OrganizationMemberAssignRequest(applicantId, OrganizationRole.OWNER);

        assertThatThrownBy(() -> organizationService.assignMember(orgId, request))
                .isInstanceOf(ForbiddenException.class);
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void assignMember_selfAssign_callerAlreadyOwner_succeeds() {
        Organization organization = approvedOrg();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(applicantId);
        when(accessGuard.hasRole(applicantId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(organizationMemberRepository.findByUserIdAndOrganizationId(applicantId, orgId)).thenReturn(Optional.empty());
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationMemberAssignRequest request = new OrganizationMemberAssignRequest(applicantId, OrganizationRole.ORGANIZER);
        OrganizationMember result = organizationService.assignMember(orgId, request);

        assertThat(result.getRoles()).contains(OrganizationRole.ORGANIZER);
    }

    @Test
    void assignMember_selfAssign_callerAlreadyOrganizer_succeeds() {
        Organization organization = approvedOrg();
        UUID organizerId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        when(organizationMemberRepository.findByUserIdAndOrganizationId(organizerId, orgId)).thenReturn(Optional.empty());
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationMemberAssignRequest request = new OrganizationMemberAssignRequest(organizerId, OrganizationRole.CHECK_IN_STAFF);
        OrganizationMember result = organizationService.assignMember(orgId, request);

        assertThat(result.getRoles()).contains(OrganizationRole.CHECK_IN_STAFF);
    }

    @Test
    void assignMember_selfAssign_callerHasNoStandingRole_throwsForbidden() {
        Organization organization = approvedOrg();
        UUID strangerId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        OrganizationMemberAssignRequest request = new OrganizationMemberAssignRequest(strangerId, OrganizationRole.CHECK_IN_STAFF);

        assertThatThrownBy(() -> organizationService.assignMember(orgId, request))
                .isInstanceOf(ForbiddenException.class);
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void assignMember_ownerAssigningAnotherUser_succeeds() {
        Organization organization = approvedOrg();
        UUID targetUserId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(applicantId);
        when(accessGuard.hasRole(applicantId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(organizationMemberRepository.findByUserIdAndOrganizationId(targetUserId, orgId)).thenReturn(Optional.empty());
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationMemberAssignRequest request = new OrganizationMemberAssignRequest(targetUserId, OrganizationRole.CHECK_IN_STAFF);
        OrganizationMember result = organizationService.assignMember(orgId, request);

        assertThat(result.getUserId()).isEqualTo(targetUserId);
        assertThat(result.getRoles()).contains(OrganizationRole.CHECK_IN_STAFF);
    }

    /**
     * BR-AUTH-005 / UC-OWNER-01's exception flow: an organizer who is NOT the
     * owner may not assign another user a role.
     */
    @Test
    void assignMember_organizerNotOwnerAssigningAnotherUser_throwsForbidden() {
        Organization organization = approvedOrg();
        UUID organizerId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);

        OrganizationMemberAssignRequest request = new OrganizationMemberAssignRequest(targetUserId, OrganizationRole.CHECK_IN_STAFF);

        assertThatThrownBy(() -> organizationService.assignMember(orgId, request))
                .isInstanceOf(ForbiddenException.class);
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void assignMember_grantsAdditionalRole_onTopOfExisting() {
        Organization organization = approvedOrg();
        UUID targetUserId = UUID.randomUUID();
        OrganizationMember existingMember = OrganizationMember.builder()
                .userId(targetUserId)
                .organizationId(orgId)
                .build();
        existingMember.getRoles().add(OrganizationRole.ORGANIZER);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(accessGuard.currentUserId()).thenReturn(applicantId);
        when(accessGuard.hasRole(applicantId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(organizationMemberRepository.findByUserIdAndOrganizationId(targetUserId, orgId)).thenReturn(Optional.of(existingMember));
        when(organizationMemberRepository.save(any(OrganizationMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationMemberAssignRequest request = new OrganizationMemberAssignRequest(targetUserId, OrganizationRole.CHECK_IN_STAFF);
        OrganizationMember result = organizationService.assignMember(orgId, request);

        assertThat(result.getRoles()).containsExactlyInAnyOrder(OrganizationRole.ORGANIZER, OrganizationRole.CHECK_IN_STAFF);
    }

    @Test
    void getOrThrow_unknownId_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(organizationRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.getOrThrow(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }
}
