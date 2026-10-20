package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.user.entity.User;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for {@link OrganizationMember}. {@code name}, {@code email} and {@code phoneNumber} come from the member's own
 * account (BR-ORG-007): read-only here, and email/phone are shown only to the owner, organizers and admins.
 */
public record OrganizationMemberResponse(
        UUID userId,
        UUID organizationId,
        Set<OrganizationRole> roles,
        Instant assignedAt,
        String name,
        String email,
        String phoneNumber,
        // BR-ORG-008: what the member may do - every permission for the owner, an organizer's chosen set, else empty
        Set<OrganizationPermission> permissions) implements Serializable {

    public static OrganizationMemberResponse from(OrganizationMember member) {
        return from(member, null, false);
    }

    /** {@code user} may be null (an account that no longer exists); contact details only when {@code withContact}. */
    public static OrganizationMemberResponse from(OrganizationMember member, User user, boolean withContact) {
        return new OrganizationMemberResponse(
                member.getUserId(),
                member.getOrganizationId(),
                member.getRoles(),
                member.getAssignedAt(),
                user == null ? null : user.fullName(),
                user == null || !withContact ? null : user.getEmail(),
                user == null || !withContact ? null : user.getPhoneNumber(),
                OrganizationAccessGuard.permissionsOf(member));
    }
}
