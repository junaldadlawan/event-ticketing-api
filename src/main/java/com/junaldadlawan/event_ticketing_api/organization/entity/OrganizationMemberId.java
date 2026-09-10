package com.junaldadlawan.event_ticketing_api.organization.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite id ({@code user_id}, {@code organization_id}) for
 * {@link OrganizationMember} — the API never exposes a surrogate member-row id.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OrganizationMemberId implements Serializable {
    private UUID userId;
    private UUID organizationId;
}
