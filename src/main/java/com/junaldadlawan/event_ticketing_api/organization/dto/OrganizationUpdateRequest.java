package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

/**
 * Body of {@code PATCH /organizations/{orgId}}: a request to change an approved organization's details (it does not change them itself, an admin
 * approves). Send only the fields to change; an empty string clears a contact detail or the description, the name cannot be empty.
 * For a PENDING application the applicant edits it directly (name, details and documents); for an approved organization the documents are part of the change request too.
 */
public record OrganizationUpdateRequest(
        @Size(max = 255)
        @NoHtml
        String name,

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
        String description,

        // replaces the organization's documents (at least one, at most DocumentDto.MAX_DOCUMENTS); for an approved organization an admin approves it
        @Size(max = DocumentDto.MAX_DOCUMENTS)
        List<@Valid DocumentDto> documents,

        // an enabled platform country (ISO 3166-1 alpha-2); the organization's currency stays the one fixed when it was approved
        @Pattern(regexp = "[A-Za-z]{2}", message = "must be a 2-letter ISO 3166-1 country code")
        String country) implements Serializable {

    public OrganizationUpdateRequest(String name, String contactEmail, String contactPhone, String address, String description) {
        this(name, contactEmail, contactPhone, address, description, null, null);
    }

    public OrganizationUpdateRequest(String name, String contactEmail, String contactPhone, String address, String description,
                                     List<DocumentDto> documents) {
        this(name, contactEmail, contactPhone, address, description, documents, null);
    }
}
