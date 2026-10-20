package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Set;

/** {@code PUT /organizations/{orgId}/members/{userId}/roles}: the member's roles become exactly this set (BR-ORG-007). */
public record OrganizationMemberRolesRequest(
        @NotNull Set<@NotNull OrganizationRole> roles) implements Serializable {
}
