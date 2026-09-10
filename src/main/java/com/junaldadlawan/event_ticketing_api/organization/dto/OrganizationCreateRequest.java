package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.organization.entity.Organization}
 * application submission (BR-ORG-001/002).
 */
public record OrganizationCreateRequest(
        @NotBlank
        @Size(max = 255)
        @NoHtml
        String name,

        @NotEmpty
        List<@Valid DocumentDto> documents) implements Serializable {
}
