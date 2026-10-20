package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Set;

/** {@code PUT /organizations/{orgId}/members/{userId}/roles}: the member's roles become exactly this set (BR-ORG-007). */
public record OrganizationMemberRolesRequest(
        @NotNull Set<@NotNull OrganizationRole> roles,
        // BR-ORG-008: only when roles includes ORGANIZER; left out = keep them, or the default set for a new organizer
        Set<@NotNull OrganizationPermission> permissions) implements Serializable {
}
