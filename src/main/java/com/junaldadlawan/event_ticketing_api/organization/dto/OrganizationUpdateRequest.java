package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.organization.entity.Organization}
 * update (owner/organizer only, approved orgs only).
 */
public record OrganizationUpdateRequest(
        @NotBlank
        @Size(max = 255)
        @NoHtml
        String name) {
}
