package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** Body of {@code POST /organizations/{orgId}/request-revision}: what the admin wants the applicant to change. */
public record OrganizationRevisionRequest(
        @NotBlank
        @Size(max = 500)
        @NoHtml
        String message) implements Serializable {
}
