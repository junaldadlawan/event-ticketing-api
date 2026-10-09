package com.junaldadlawan.event_ticketing_api.category.repository;

import com.junaldadlawan.event_ticketing_api.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscNameAsc();

    Optional<Category> findByNameIgnoreCaseAndDeletedAtIsNull(String name);

    Optional<Category> findBySlugAndDeletedAtIsNull(String slug);
}
