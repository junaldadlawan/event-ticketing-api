package com.junaldadlawan.event_ticketing_api.organization.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditDetails;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
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
import com.junaldadlawan.event_ticketing_api.organization.specification.OrganizationSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationAccessGuard accessGuard;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    @Override
    public Organization apply(OrganizationCreateRequest request) {
        Organization organization = Organization.builder()
                .name(request.name())
                .status(OrganizationStatus.PENDING)
                .documents(request.documents().stream().map(DocumentDto::toEntity).toList())
                .build();
        Organization saved = organizationRepository.save(organization);
        auditLogService.recordByCurrentUser("organization.applied", "Organization", saved.getId(), null, saved.getId(),
                AuditDetails.named(organization.getName()));
        return saved;
    }

    @Override
    public List<Organization> list(String status) {
        accessGuard.requireAdmin();
        OrganizationStatus statusFilter = parseStatus(status);
        Specification<Organization> specification = Specification
                .where(OrganizationSpecification.hasStatus(statusFilter))
                .and(OrganizationSpecification.notDeleted());
        return organizationRepository.findAll(specification);
    }

    @Override
    public List<Organization> mine() {
        UUID callerId = accessGuard.currentUserId();
        List<UUID> memberOf = organizationMemberRepository.findByUserId(callerId).stream()
                .map(OrganizationMember::getOrganizationId).toList();
        // One batched lookup for the memberships, one for the applications; merged by id.
        Map<UUID, Organization> byId = new LinkedHashMap<>();
        organizationRepository.findAllById(memberOf).stream().filter(org -> org.getDeletedAt() == null)
                .forEach(org -> byId.put(org.getId(), org));
        organizationRepository.findByCreatedByAndDeletedAtIsNull(callerId.toString()).forEach(org -> byId.put(org.getId(), org));
        return byId.values().stream()
                .sorted(Comparator.comparing(Organization::getCreatedAt).thenComparing(Organization::getId))
                .toList();
    }

    @Override
    public Organization get(UUID orgId) {
        Organization organization = getOrThrow(orgId);
        UUID callerId = accessGuard.currentUserId();
        boolean isApplicant = isCreatedBy(organization, callerId);
        if (!isApplicant && !accessGuard.isMember(callerId, orgId) && !accessGuard.isAdmin()) {
            throw new ForbiddenException("Not authorized to view this organization");
        }
        return organization;
    }

    @Override
    public Organization update(UUID orgId, OrganizationUpdateRequest request) {
        Organization organization = getOrThrow(orgId);
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, orgId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the organization's owner or organizer may update it");
        }
        if (organization.getStatus() != OrganizationStatus.APPROVED) {
            throw new ForbiddenException("Only an approved organization may be updated");
        }
        Map<String, Object> before = AuditDetails.snapshot(organization, OrganizationUpdateRequest.class);
        organization.setName(request.name());
        Organization saved = organizationRepository.save(organization);
        // BR-ADMIN-007: only the fields that actually changed; an update that changed nothing is not logged
        Map<String, Object> changes = AuditDetails.changes(before, AuditDetails.snapshot(organization, OrganizationUpdateRequest.class));
        if (changes != null) {
            auditLogService.recordByCurrentUser("organization.updated", "Organization", orgId, null, orgId, changes);
        }
        return saved;
    }

    @Override
    public Organization approve(UUID orgId) {
        accessGuard.requireAdmin();
        Organization organization = getOrThrow(orgId);
        if (organization.getStatus() != OrganizationStatus.PENDING) {
            throw new ConflictException("Organization application is not pending");
        }
        UUID applicantId = UUID.fromString(organization.getCreatedBy());
        organization.setStatus(OrganizationStatus.APPROVED);
        organization.setOwnerId(applicantId);
        organizationRepository.save(organization);

        grantRole(applicantId, orgId, OrganizationRole.OWNER);
        auditLogService.recordByCurrentUser("organization.approved", "Organization", orgId, null, orgId,
                AuditDetails.named(organization.getName()));

        return organization;
    }

    @Override
    public Organization reject(UUID orgId, OrganizationRejectRequest request) {
        accessGuard.requireAdmin();
        Organization organization = getOrThrow(orgId);
        if (organization.getStatus() != OrganizationStatus.PENDING) {
            throw new ConflictException("Organization application is not pending");
        }
        organization.setStatus(OrganizationStatus.REJECTED);
        organization.setRejectionReason(request != null ? request.reason() : null);
        Organization saved = organizationRepository.save(organization);
        Map<String, Object> details = AuditDetails.named(organization.getName());
        Map<String, Object> reason = AuditDetails.reason(organization.getRejectionReason());
        if (reason != null) {
            details.putAll(reason);
        }
        auditLogService.recordByCurrentUser("organization.rejected", "Organization", orgId, null, orgId, details);
        return saved;
    }

    @Override
    public List<OrganizationMember> listMembers(UUID orgId) {
        getOrThrow(orgId);
        UUID callerId = accessGuard.currentUserId();
        if (!accessGuard.isMember(callerId, orgId) && !accessGuard.isAdmin()) {
            throw new ForbiddenException("Not authorized to view this organization's members");
        }
        return organizationMemberRepository.findByOrganizationId(orgId);
    }

    @Override
    public OrganizationMember assignMember(UUID orgId, OrganizationMemberAssignRequest request) {
        getOrThrow(orgId);
        if (request.role() == OrganizationRole.OWNER) {
            throw new ForbiddenException("The owner role cannot be granted through this endpoint");
        }
        UUID callerId = accessGuard.currentUserId();
        UUID targetUserId = request.userId();

        if (targetUserId.equals(callerId)) {
            boolean callerHasStandingRole = accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)
                    || accessGuard.hasRole(callerId, orgId, OrganizationRole.ORGANIZER);
            if (!callerHasStandingRole) {
                throw new ForbiddenException("Must already hold a role in this organization to self-assign");
            }
        } else {
            if (!accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)) {
                throw new ForbiddenException("Only the organization's owner may assign roles to other users");
            }
        }

        OrganizationMember member = grantRole(targetUserId, orgId, request.role());
        // BR-NFR-005 (Phase 13): sensitive-action audit trail. targetId is
        // the organization whose membership changed - OrganizationMember has
        // no standalone id of its own callers would find useful to look up.
        Map<String, Object> details = AuditDetails.named(userRepository.findById(targetUserId).map(User::fullName).orElse(null));
        details.put("role", request.role().name());
        auditLogService.record(callerId, "organization_member.assigned", "OrganizationMember", orgId, null, orgId, details);
        return member;
    }

    /**
     * Creates the {@link OrganizationMember} row if it doesn't exist yet and
     * adds {@code role} to it.
     */
    private OrganizationMember grantRole(UUID userId, UUID organizationId, OrganizationRole role) {
        OrganizationMember member = organizationMemberRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .orElseGet(() -> OrganizationMember.builder()
                        .userId(userId)
                        .organizationId(organizationId)
                        .build());
        member.getRoles().add(role);
        return organizationMemberRepository.save(member);
    }

    public Organization getOrThrow(UUID orgId) {
        return organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization " + orgId + " not found"));
    }

    private boolean isCreatedBy(Organization organization, UUID callerId) {
        try {
            return callerId.equals(UUID.fromString(organization.getCreatedBy()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Case-insensitive parse of the {@code ?status=} filter (openapi's enum
     * values are lowercase; the stored/Java enum is uppercase). An
     * unrecognized value is treated the same as "not provided" (no filter)
     * rather than a hard error — consistent with this codebase's existing
     * null-safe {@code Specification} filter convention (see
     * {@code EventSpecification.hasCategory}), and deliberately not a thrown
     * exception: any exception type not caught by {@code GlobalExceptionHandler}
     * (which only maps {@code ApiException}) falls through to Spring's
     * default error handling, which forwards to {@code /error} — and that
     * forwarded dispatch re-enters {@code SecurityConfig}'s authorization
     * filter with no {@code SecurityContext}, so the custom
     * authenticationEntryPoint fires and masks the real status with a
     * misleading 401. That's a pre-existing gap in
     * GlobalExceptionHandler/SecurityConfig, out of scope for this change —
     * flagged for follow-up rather than fixed here.
     */
    private OrganizationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrganizationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
