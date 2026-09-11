package com.junaldadlawan.event_ticketing_api.tickettemplate.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTemplateUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import com.junaldadlawan.event_ticketing_api.tickettemplate.repository.TicketTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link TicketTemplateServiceImpl} (no Spring
 * context), mirroring {@code TicketTypeServiceImplTest}'s style. Closes the
 * code-reviewer MEDIUM finding: zero prior coverage for the
 * {@code tickettemplate/} module. Covers BR-TICKET-007/010, BR-AUTH-002/004,
 * and the two behaviors the dispatch flagged as security-load-bearing for
 * this module: (1) {@code list()} is owning-organizer/admin-only ALWAYS,
 * with no draft/published-based public visibility split (unlike
 * {@code TicketTypeServiceImpl.list}), and (2) {@code update()} resolves its
 * owning organization strictly from the persisted template's own
 * {@code eventId}, never from client input.
 */
@ExtendWith(MockitoExtension.class)
class TicketTemplateServiceImplTest {

    @Mock
    private TicketTemplateRepository ticketTemplateRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    private TicketTemplateServiceImpl service;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new TicketTemplateServiceImpl(ticketTemplateRepository, eventRepository, accessGuard);
        orgId = UUID.randomUUID();
    }

    private Event event(UUID id, UUID organizationId, EventStatus status) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return Event.builder()
                .id(id)
                .organizationId(organizationId)
                .title("Concert Night")
                .description("desc")
                .category("music")
                .status(status)
                .ticketPrefix("ABC")
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
    }

    private TicketTemplate template(UUID id, UUID eventId) {
        return TicketTemplate.builder()
                .id(id)
                .eventId(eventId)
                .ticketTypeId(null)
                .format(TicketTemplateFormat.DIGITAL)
                .logoUrl("https://example.com/logo.png")
                .backgroundImageUrl("https://example.com/bg.png")
                .primaryColor("#112233")
                .build();
    }

    private TicketTemplateCreateRequest createRequest(UUID ticketTypeId) {
        return new TicketTemplateCreateRequest(
                ticketTypeId,
                TicketTemplateFormat.DIGITAL,
                "https://example.com/logo.png",
                "https://example.com/bg.png",
                "#ABCDEF");
    }

    // ---- create() ----

    @Test
    void create_owner_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketTemplate result = service.create(eventId, createRequest(null));

        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getFormat()).isEqualTo(TicketTemplateFormat.DIGITAL);
        assertThat(result.getLogoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(result.getBackgroundImageUrl()).isEqualTo("https://example.com/bg.png");
        assertThat(result.getPrimaryColor()).isEqualTo("#ABCDEF");
        assertThat(result.getTicketTypeId()).isNull();
    }

    @Test
    void create_organizer_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketTemplate result = service.create(eventId, createRequest(null));

        assertThat(result.getEventId()).isEqualTo(eventId);
    }

    /** BR-AUTH-004: admin bypass must short-circuit before any org-role lookup. */
    @Test
    void create_adminWithNoOrgRole_bypassesOrgRoleCheck() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketTemplate result = service.create(eventId, createRequest(null));

        assertThat(result.getEventId()).isEqualTo(eventId);
        verify(accessGuard, never()).currentUserId();
        verify(accessGuard, never()).hasRole(any(), any(), any());
    }

    @Test
    void create_ticketTypeScoped_persistsTicketTypeId() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketTemplate result = service.create(eventId, createRequest(ticketTypeId));

        assertThat(result.getTicketTypeId()).isEqualTo(ticketTypeId);
    }

    @Test
    void create_nonExistentEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(eventId, createRequest(null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ticketTemplateRepository, never()).save(any());
        verifyNoInteractions(accessGuard);
    }

    @Test
    void create_stranger_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.create(eventId, createRequest(null)))
                .isInstanceOf(ForbiddenException.class);
        verify(ticketTemplateRepository, never()).save(any());
    }

    /**
     * Key regression class (same as every other module in this codebase): an
     * owner/organizer of a DIFFERENT organization must be forbidden too, not
     * just a roleless stranger.
     */
    @Test
    void create_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.create(eventId, createRequest(null)))
                .isInstanceOf(ForbiddenException.class);
        verify(accessGuard).hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER);
        verify(accessGuard, never()).hasRole(otherOrgOwnerId, otherOrgId, OrganizationRole.OWNER);
        verify(ticketTemplateRepository, never()).save(any());
    }

    // ---- list() ----

    /**
     * The security-relevant behavior this module deliberately differs on
     * from {@code TicketTypeServiceImpl.list}: there is no public/draft-based
     * visibility split here at all — listing templates is owning-organizer/
     * admin-only regardless of the event's status.
     */
    @Test
    void list_strangerOnPublishedEvent_stillThrowsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.PUBLISHED)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.list(eventId)).isInstanceOf(ForbiddenException.class);
        verify(ticketTemplateRepository, never()).findByEventIdAndDeletedAtIsNull(any());
    }

    @Test
    void list_owner_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.PUBLISHED)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(ticketTemplateRepository.findByEventIdAndDeletedAtIsNull(eventId))
                .thenReturn(List.of(template(UUID.randomUUID(), eventId)));

        List<TicketTemplate> result = service.list(eventId);

        assertThat(result).hasSize(1);
    }

    @Test
    void list_admin_succeeds() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.PUBLISHED)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketTemplateRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(List.of());

        service.list(eventId);

        verify(accessGuard, never()).currentUserId();
    }

    @Test
    void list_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(eventId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard);
    }

    // ---- update() : authorization, resolved from the TEMPLATE's own eventId ----

    private void mockOwnerAccess(UUID templateId, UUID eventId) {
        UUID ownerId = UUID.randomUUID();
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template(templateId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void update_owner_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId, new TicketTemplateUpdateRequest("https://x.com/new-logo.png", null, null));

        assertThat(result.getLogoUrl()).isEqualTo("https://x.com/new-logo.png");
    }

    @Test
    void update_unknownTemplate_throwsResourceNotFound() {
        UUID templateId = UUID.randomUUID();
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(templateId, new TicketTemplateUpdateRequest(null, null, "#000000")))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard);
        verify(ticketTemplateRepository, never()).save(any());
    }

    @Test
    void update_stranger_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template(templateId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.update(templateId, new TicketTemplateUpdateRequest(null, null, "#000000")))
                .isInstanceOf(ForbiddenException.class);
        verify(ticketTemplateRepository, never()).save(any());
    }

    /**
     * The dispatch's specifically-requested regression: a cross-org
     * organizer crafting a PATCH request against a template that does NOT
     * belong to their own organization must be forbidden — this is only
     * possible to get right because {@code update()} resolves the owning
     * organization from the persisted template's own {@code eventId}, never
     * from any client-supplied field (the update DTO doesn't even carry one).
     */
    @Test
    void update_crossOrgOrganizer_throwsForbidden_resolvedFromTemplatesOwnEventId() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();
        UUID otherOrgOrganizerId = UUID.randomUUID();
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template(templateId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOrganizerId);
        when(accessGuard.hasRole(otherOrgOrganizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOrganizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.update(templateId, new TicketTemplateUpdateRequest(null, null, "#000000")))
                .isInstanceOf(ForbiddenException.class);
        // Never even checked against the attacker's own org - proves resolution
        // came from the template's real eventId/organizationId, not any input.
        verify(accessGuard, never()).hasRole(otherOrgOrganizerId, otherOrgId, OrganizationRole.OWNER);
        verify(accessGuard, never()).hasRole(otherOrgOrganizerId, otherOrgId, OrganizationRole.ORGANIZER);
        verify(ticketTemplateRepository, never()).save(any());
    }

    @Test
    void update_admin_succeedsWithNoOrgRole() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template(templateId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketTemplate result = service.update(templateId, new TicketTemplateUpdateRequest(null, null, "#000000"));

        assertThat(result.getPrimaryColor()).isEqualTo("#000000");
        verify(accessGuard, never()).currentUserId();
    }

    // ---- update() : partial-update matrix (only branding fields, null = unchanged) ----

    @Test
    void update_logoUrlOnly_changesOnlyLogoUrl() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId, new TicketTemplateUpdateRequest("https://new.example.com/logo.png", null, null));

        assertThat(result.getLogoUrl()).isEqualTo("https://new.example.com/logo.png");
        assertThat(result.getBackgroundImageUrl()).isEqualTo("https://example.com/bg.png");
        assertThat(result.getPrimaryColor()).isEqualTo("#112233");
    }

    @Test
    void update_backgroundImageUrlOnly_changesOnlyThatField() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId, new TicketTemplateUpdateRequest(null, "https://new.example.com/bg.png", null));

        assertThat(result.getBackgroundImageUrl()).isEqualTo("https://new.example.com/bg.png");
        assertThat(result.getLogoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(result.getPrimaryColor()).isEqualTo("#112233");
    }

    @Test
    void update_primaryColorOnly_changesOnlyThatField() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId, new TicketTemplateUpdateRequest(null, null, "#FEFEFE"));

        assertThat(result.getPrimaryColor()).isEqualTo("#FEFEFE");
        assertThat(result.getLogoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(result.getBackgroundImageUrl()).isEqualTo("https://example.com/bg.png");
    }

    @Test
    void update_allFieldsNull_leavesTemplateUnchanged() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId, new TicketTemplateUpdateRequest(null, null, null));

        assertThat(result.getLogoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(result.getBackgroundImageUrl()).isEqualTo("https://example.com/bg.png");
        assertThat(result.getPrimaryColor()).isEqualTo("#112233");
    }
}
