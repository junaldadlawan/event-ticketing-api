package com.junaldadlawan.event_ticketing_api.category.controller;

import com.junaldadlawan.event_ticketing_api.category.dto.CategoryCreateRequest;
import com.junaldadlawan.event_ticketing_api.category.dto.CategoryResponse;
import com.junaldadlawan.event_ticketing_api.category.dto.CategoryUpdateRequest;
import com.junaldadlawan.event_ticketing_api.category.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.listActive().stream().map(CategoryResponse::from).toList();
    }

    @GetMapping("/{categoryId}")
    public CategoryResponse get(@PathVariable UUID categoryId) {
        return CategoryResponse.from(categoryService.get(categoryId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryCreateRequest request) {
        return CategoryResponse.from(categoryService.create(request));
    }

    @PatchMapping("/{categoryId}")
    public CategoryResponse update(@PathVariable UUID categoryId, @Valid @RequestBody CategoryUpdateRequest request) {
        return CategoryResponse.from(categoryService.update(categoryId, request));
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID categoryId) {
        categoryService.deactivate(categoryId);
    }
}
