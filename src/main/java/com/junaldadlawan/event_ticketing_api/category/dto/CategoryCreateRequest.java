package com.junaldadlawan.event_ticketing_api.category.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating an event category (admin only). {@code sortOrder} is
 * optional and defaults to 0.
 */
public record CategoryCreateRequest(
        @NotBlank
        @Size(max = 100)
        @NoHtml
        String name,

        @Size(max = 255)
        @NoHtml
        String description,

        Integer sortOrder) {
}
