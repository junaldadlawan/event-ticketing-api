package com.junaldadlawan.event_ticketing_api.category.service;

import com.junaldadlawan.event_ticketing_api.category.dto.CategoryCreateRequest;
import com.junaldadlawan.event_ticketing_api.category.dto.CategoryUpdateRequest;
import com.junaldadlawan.event_ticketing_api.category.entity.Category;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    /** Public: active categories, ordered by sortOrder then name. */
    List<Category> listActive();

    Category get(UUID categoryId);

    /** Admin only. */
    Category create(CategoryCreateRequest request);

    /** Admin only. */
    Category update(UUID categoryId, CategoryUpdateRequest request);

    /** Admin only. Soft: sets {@code active=false}; never removes the row. */
    void deactivate(UUID categoryId);

    /**
     * Resolves the (case-insensitive) name an event supplied to the matching
     * ACTIVE category, so callers store its canonical spelling.
     *
     * @throws com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException
     *         if no active category has that name
     */
    Category resolveActive(String name);
}
