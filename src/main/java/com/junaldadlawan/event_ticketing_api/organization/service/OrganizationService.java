package com.junaldadlawan.event_ticketing_api.organization.service;

import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationCreateRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationMemberAssignRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationMemberResponse;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationRejectRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationUpdateRequest;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface OrganizationService {
    Organization apply(OrganizationCreateRequest request);

    List<Organization> list(String status);

    /** The caller's own organizations, of every status: ones they are a member of, plus ones they applied for. Oldest first. */
    List<Organization> mine();

    Organization get(UUID orgId);

    Organization update(UUID orgId, OrganizationUpdateRequest request);

    Organization approve(UUID orgId);

    Organization reject(UUID orgId, OrganizationRejectRequest request);

    List<OrganizationMember> listMembers(UUID orgId);

    /**
     * BR-ORG-007: the members with who they are - name for every member who may list them; email and phone only for
     * the owner, organizers and admins (check-in-only staff see names).
     */
    List<OrganizationMemberResponse> listMembersWithDetails(UUID orgId);

    OrganizationMember assignMember(UUID orgId, OrganizationMemberAssignRequest request);

    /** BR-ORG-007: the member's roles become exactly {@code roles}. The owner or a TEAM organizer (BR-ORG-008). */
    OrganizationMemberResponse setMemberRoles(UUID orgId, UUID userId, Set<OrganizationRole> roles,
                                              Set<OrganizationPermission> permissions);

    /** BR-ORG-008: the caller's own membership - roles and permissions - so the web app knows which tabs to show. */
    OrganizationMemberResponse myMembership(UUID orgId);

    /** BR-ORG-007: removes the member and all their roles. The owner or a TEAM organizer; never the owner. */
    void removeMember(UUID orgId, UUID userId);
}
