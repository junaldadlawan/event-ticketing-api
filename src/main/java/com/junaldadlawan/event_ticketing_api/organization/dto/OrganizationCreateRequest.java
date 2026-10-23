package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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
        @Size(max = DocumentDto.MAX_DOCUMENTS)
        List<@Valid DocumentDto> documents,

        // optional details, with the same limits as OrganizationUpdateRequest; they are the organization's initial values (an admin reviews the whole application)
        @Email
        @Size(max = 255)
        String contactEmail,

        @Size(max = 30)
        @Pattern(regexp = "|[0-9+()\\-. ]{5,30}", message = "must be a phone number of digits, spaces and + ( ) - .")
        String contactPhone,

        @Size(max = 500)
        @NoHtml
        String address,

        @Size(max = 2000)
        @NoHtml
        String description) implements Serializable {

    /** An application without the optional details. */
    public OrganizationCreateRequest(String name, List<DocumentDto> documents) {
        this(name, documents, null, null, null, null);
    }
}
