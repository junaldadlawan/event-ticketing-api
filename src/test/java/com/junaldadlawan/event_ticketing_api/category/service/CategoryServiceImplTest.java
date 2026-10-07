package com.junaldadlawan.event_ticketing_api.category.service;

import com.junaldadlawan.event_ticketing_api.category.dto.CategoryCreateRequest;
import com.junaldadlawan.event_ticketing_api.category.dto.CategoryUpdateRequest;
import com.junaldadlawan.event_ticketing_api.category.entity.Category;
import com.junaldadlawan.event_ticketing_api.category.repository.CategoryRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryServiceImpl(categoryRepository, accessGuard);
    }

    private Category category(String name, boolean active) {
        return Category.builder().id(UUID.randomUUID()).name(name).slug(CategoryServiceImpl.slugify(name))
                .active(active).build();
    }

    // ---- slugify ----

    @Test
    void slugify_replacesSymbolsAndTrimsDashes() {
        assertThat(CategoryServiceImpl.slugify("Arts & Theatre")).isEqualTo("arts-theatre");
        assertThat(CategoryServiceImpl.slugify("  Food   & Drink! ")).isEqualTo("food-drink");
    }

    @Test
    void slugify_nameWithNoLettersOrDigits_throwsBadRequest() {
        assertThatThrownBy(() -> CategoryServiceImpl.slugify("&&&")).isInstanceOf(BadRequestException.class);
    }

    // ---- create ----

    @Test
    void create_admin_savesTrimmedNameAndSlug() {
        when(categoryRepository.findByNameIgnoreCaseAndDeletedAtIsNull("Esports")).thenReturn(Optional.empty());
        when(categoryRepository.findBySlugAndDeletedAtIsNull("esports")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category result = service.create(new CategoryCreateRequest("  Esports ", "Competitive gaming", 130));

        assertThat(result.getName()).isEqualTo("Esports");
        assertThat(result.getSlug()).isEqualTo("esports");
        assertThat(result.getSortOrder()).isEqualTo(130);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void create_nonAdmin_throwsForbidden_andSavesNothing() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.create(new CategoryCreateRequest("Esports", null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_duplicateNameIgnoringCase_throwsConflict() {
        when(categoryRepository.findByNameIgnoreCaseAndDeletedAtIsNull("music"))
                .thenReturn(Optional.of(category("Music", true)));

        assertThatThrownBy(() -> service.create(new CategoryCreateRequest("music", null, null)))
                .isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_sameSlugDifferentName_throwsConflict() {
        when(categoryRepository.findByNameIgnoreCaseAndDeletedAtIsNull("Arts + Theatre")).thenReturn(Optional.empty());
        when(categoryRepository.findBySlugAndDeletedAtIsNull("arts-theatre"))
                .thenReturn(Optional.of(category("Arts & Theatre", true)));

        assertThatThrownBy(() -> service.create(new CategoryCreateRequest("Arts + Theatre", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    // ---- update ----

    @Test
    void update_partialFields_changesOnlyThose() {
        Category existing = category("Music", true);
        when(categoryRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category result = service.update(existing.getId(), new CategoryUpdateRequest(null, "Live music", 5, null));

        assertThat(result.getName()).isEqualTo("Music");
        assertThat(result.getDescription()).isEqualTo("Live music");
        assertThat(result.getSortOrder()).isEqualTo(5);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void update_renameToItself_isNotAConflict() {
        Category existing = category("Music", true);
        when(categoryRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(categoryRepository.findByNameIgnoreCaseAndDeletedAtIsNull("MUSIC")).thenReturn(Optional.of(existing));
        when(categoryRepository.findBySlugAndDeletedAtIsNull("music")).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category result = service.update(existing.getId(), new CategoryUpdateRequest("MUSIC", null, null, null));

        assertThat(result.getName()).isEqualTo("MUSIC");
    }

    @Test
    void update_renameToAnotherCategory_throwsConflict() {
        Category existing = category("Music", true);
        when(categoryRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(categoryRepository.findByNameIgnoreCaseAndDeletedAtIsNull("Sports"))
                .thenReturn(Optional.of(category("Sports", true)));

        assertThatThrownBy(() -> service.update(existing.getId(), new CategoryUpdateRequest("Sports", null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_blankName_throwsBadRequest() {
        Category existing = category("Music", true);
        when(categoryRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(), new CategoryUpdateRequest("   ", null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_canReactivate() {
        Category existing = category("Music", false);
        when(categoryRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.update(existing.getId(), new CategoryUpdateRequest(null, null, null, true)).isActive())
                .isTrue();
    }

    @Test
    void update_unknownCategory_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new CategoryUpdateRequest("X", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- deactivate ----

    @Test
    void deactivate_setsInactive_withoutDeleting() {
        Category existing = category("Music", true);
        when(categoryRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        service.deactivate(existing.getId());

        assertThat(existing.isActive()).isFalse();
        verify(categoryRepository).save(existing);
        verify(categoryRepository, never()).delete(any());
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void deactivate_nonAdmin_throwsForbidden() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.deactivate(UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
        verify(categoryRepository, never()).save(any());
    }

    // ---- resolveActive ----

    @Test
    void resolveActive_returnsTheCanonicalCategory_ignoringCase() {
        Category music = category("Music", true);
        when(categoryRepository.findByNameIgnoreCaseAndDeletedAtIsNull("music")).thenReturn(Optional.of(music));

        assertThat(service.resolveActive("  music ").getName()).isEqualTo("Music");
    }

    @Test
    void resolveActive_unknownName_throwsBadRequest() {
        when(categoryRepository.findByNameIgnoreCaseAndDeletedAtIsNull("nonsense")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveActive("nonsense")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void resolveActive_inactiveCategory_throwsBadRequest() {
        when(categoryRepository.findByNameIgnoreCaseAndDeletedAtIsNull("Music"))
                .thenReturn(Optional.of(category("Music", false)));

        assertThatThrownBy(() -> service.resolveActive("Music")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void resolveActive_null_throwsBadRequest() {
        assertThatThrownBy(() -> service.resolveActive(null)).isInstanceOf(BadRequestException.class);
    }
}
