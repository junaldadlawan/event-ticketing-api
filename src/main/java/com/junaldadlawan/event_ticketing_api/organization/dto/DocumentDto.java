package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * A single verification document (e.g. business permit) submitted with an
 * organization application.
 */
public record DocumentDto(
        @NotBlank
        @Size(max = 100)
        @NoHtml
        String type,

        @NotBlank
        @Size(max = 500)
        @NoHtml
        @jakarta.validation.constraints.Pattern(regexp = "(?i)https?://\\S+", message = "must be an http or https address")
        String url) implements Serializable {

    /** The most documents an application or an organization may hold. */
    public static final int MAX_DOCUMENTS = 10;

    public static DocumentDto from(OrganizationDocument document) {
        return new DocumentDto(document.getType(), document.getUrl());
    }

    public OrganizationDocument toEntity() {
        return OrganizationDocument.builder()
                .type(type)
                .url(url)
                .build();
    }
}
