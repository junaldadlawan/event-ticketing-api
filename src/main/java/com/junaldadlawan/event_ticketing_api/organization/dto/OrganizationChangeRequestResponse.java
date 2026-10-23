package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.organization.enums.ChangeRequestStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A requested edit of an organization. {@code requested} holds the changed fields only (null value = clear the field; `documents` is a list of {type, url}); {@code current} the
 * organization's values for those same fields right now, so a reviewer sees both side by side.
 */
public record OrganizationChangeRequestResponse(
        UUID id,
        UUID organizationId,
        String organizationName,
        UUID requestedBy,
        Map<String, Object> requested,
        Map<String, Object> current,
        ChangeRequestStatus status,
        Instant createdAt,
        UUID decidedBy,
        Instant decidedAt,
        String rejectionReason) implements Serializable {
}
