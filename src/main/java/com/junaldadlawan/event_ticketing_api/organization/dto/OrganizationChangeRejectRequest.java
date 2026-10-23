package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** Body of {@code POST /admin/organization-change-requests/{id}/reject}: the reason is required. */
public record OrganizationChangeRejectRequest(
        @NotBlank
        @Size(max = 500)
        @NoHtml
        String reason) implements Serializable {
}
