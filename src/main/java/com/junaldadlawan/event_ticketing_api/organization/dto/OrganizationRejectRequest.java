package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.Size;

/** Optional rejection reason, persisted to {@code organizations.rejection_reason}. */
public record OrganizationRejectRequest(
        @Size(max = 500)
        @NoHtml
        String reason) {
}
