package com.junaldadlawan.event_ticketing_api.category.service;

import com.junaldadlawan.event_ticketing_api.category.dto.CategoryCreateRequest;
import com.junaldadlawan.event_ticketing_api.category.dto.CategoryUpdateRequest;
import com.junaldadlawan.event_ticketing_api.category.entity.Category;
import com.junaldadlawan.event_ticketing_api.category.repository.CategoryRepository;
import com.junaldadlawan.event_ticketing_api.common.config.CacheConfig;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final OrganizationAccessGuard accessGuard;

    /** Cached: the public list is tiny, read on every event form, and changes only on an admin write. */
    @Override
    @Cacheable(cacheNames = CacheConfig.CATEGORIES, key = "'active'")
    public List<Category> listActive() {
        return categoryRepository.findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscNameAsc();
    }

    @Override
    public Category get(UUID categoryId) {
        return getOrThrow(categoryId);
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)
    public Category create(CategoryCreateRequest request) {
        accessGuard.requireAdmin();
        String name = request.name().trim();
        String slug = slugify(name);
        requireUnique(name, slug, null);

        Category saved = categoryRepository.save(Category.builder()
                .name(name)
                .slug(slug)
                .description(request.description())
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .build());
        BusinessAuditLogger.record("category.created", "Category", saved.getId(),
                BusinessAuditLogger.Outcome.SUCCESS, "name=" + saved.getName());
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)
    public Category update(UUID categoryId, CategoryUpdateRequest request) {
        accessGuard.requireAdmin();
        Category category = getOrThrow(categoryId);

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new BadRequestException("Category name must not be blank");
            }
            String slug = slugify(name);
            requireUnique(name, slug, categoryId);
            category.setName(name);
            category.setSlug(slug);
        }
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
        Category saved = categoryRepository.save(category);
        BusinessAuditLogger.record("category.updated", "Category", categoryId, BusinessAuditLogger.Outcome.SUCCESS);
        return saved;
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)
    public void deactivate(UUID categoryId) {
        accessGuard.requireAdmin();
        Category category = getOrThrow(categoryId);
        category.setActive(false);
        categoryRepository.save(category);
        BusinessAuditLogger.record("category.deactivated", "Category", categoryId,
                BusinessAuditLogger.Outcome.SUCCESS, "name=" + category.getName());
    }

    @Override
    public Category resolveActive(String name) {
        return categoryRepository.findByNameIgnoreCaseAndDeletedAtIsNull(name == null ? "" : name.trim())
                .filter(Category::isActive)
                .orElseThrow(() -> new BadRequestException(
                        "Unknown event category '" + name + "'. See GET /api/v1/categories for the allowed values"));
    }

    public Category getOrThrow(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(category -> category.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + categoryId + " not found"));
    }

    private void requireUnique(String name, String slug, UUID selfId) {
        categoryRepository.findByNameIgnoreCaseAndDeletedAtIsNull(name)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new ConflictException("Category '" + existing.getName() + "' already exists");
                });
        categoryRepository.findBySlugAndDeletedAtIsNull(slug)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new ConflictException("Category '" + existing.getName() + "' already uses the same slug");
                });
    }

    /** "Arts & Theatre" -> "arts-theatre". */
    static String slugify(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            throw new BadRequestException("Category name must contain letters or digits");
        }
        return slug;
    }
}
