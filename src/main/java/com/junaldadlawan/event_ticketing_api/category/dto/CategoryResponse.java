package com.junaldadlawan.event_ticketing_api.category.dto;

import com.junaldadlawan.event_ticketing_api.category.entity.Category;

import java.io.Serializable;
import java.util.UUID;

/**
 * DTO for {@link Category}
 */
public record CategoryResponse(
        UUID id,
        String name,
        String slug,
        String description,
        boolean active,
        int sortOrder) implements Serializable {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.isActive(),
                category.getSortOrder());
    }
}
