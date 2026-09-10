package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO for assigning an {@link OrganizationRole} to a user within an
 * organization. {@code role == OWNER} is rejected at the service layer, not
 * the validation layer (it's a business rule, not a shape constraint —
 * OWNER is only ever set via organization-application approval).
 */
public record OrganizationMemberAssignRequest(
        @NotNull UUID userId,
        @NotNull OrganizationRole role) {
}
