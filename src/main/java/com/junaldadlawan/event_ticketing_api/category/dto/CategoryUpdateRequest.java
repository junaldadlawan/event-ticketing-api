package com.junaldadlawan.event_ticketing_api.category.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.Size;

/**
 * DTO for a partial category update (admin only). All fields optional;
 * {@code active=false} hides the category from the public list and from new
 * events without touching events that already use it.
 */
public record CategoryUpdateRequest(
        @Size(max = 100)
        @NoHtml
        String name,

        @Size(max = 255)
        @NoHtml
        String description,

        Integer sortOrder,

        Boolean active) {
}
