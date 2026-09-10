package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for {@link OrganizationMember}
 */
public record OrganizationMemberResponse(
        UUID userId,
        UUID organizationId,
        Set<OrganizationRole> roles,
        Instant assignedAt) implements Serializable {

    public static OrganizationMemberResponse from(OrganizationMember member) {
        return new OrganizationMemberResponse(
                member.getUserId(),
                member.getOrganizationId(),
                member.getRoles(),
                member.getAssignedAt());
    }
}
