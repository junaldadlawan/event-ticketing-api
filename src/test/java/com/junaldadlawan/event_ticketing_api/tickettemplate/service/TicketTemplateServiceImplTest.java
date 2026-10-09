package com.junaldadlawan.event_ticketing_api.tickettemplate.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
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
import com.junaldadlawan.event_ticketing_api.tickettemplate.dto.TicketTextFieldDto;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTextField;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.BackgroundFit;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextAlign;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextFieldKey;
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
import static org.mockito.Mockito.lenient;
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
                "#ABCDEF",
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
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
        lenient().when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void update_owner_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId, new TicketTemplateUpdateRequest("https://x.com/new-logo.png", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.getLogoUrl()).isEqualTo("https://x.com/new-logo.png");
    }

    @Test
    void update_unknownTemplate_throwsResourceNotFound() {
        UUID templateId = UUID.randomUUID();
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(templateId, new TicketTemplateUpdateRequest(null, null, "#000000", null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
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

        assertThatThrownBy(() -> service.update(templateId, new TicketTemplateUpdateRequest(null, null, "#000000", null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
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

        assertThatThrownBy(() -> service.update(templateId, new TicketTemplateUpdateRequest(null, null, "#000000", null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
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

        TicketTemplate result = service.update(templateId, new TicketTemplateUpdateRequest(null, null, "#000000", null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.getPrimaryColor()).isEqualTo("#000000");
        verify(accessGuard, never()).currentUserId();
    }

    // ---- update() : partial-update matrix (only branding fields, null = unchanged) ----

    @Test
    void update_logoUrlOnly_changesOnlyLogoUrl() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId, new TicketTemplateUpdateRequest("https://new.example.com/logo.png", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.getLogoUrl()).isEqualTo("https://new.example.com/logo.png");
        assertThat(result.getBackgroundImageUrl()).isEqualTo("https://example.com/bg.png");
        assertThat(result.getPrimaryColor()).isEqualTo("#112233");
    }

    @Test
    void update_backgroundImageUrlOnly_changesOnlyThatField() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId, new TicketTemplateUpdateRequest(null, "https://new.example.com/bg.png", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.getBackgroundImageUrl()).isEqualTo("https://new.example.com/bg.png");
        assertThat(result.getLogoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(result.getPrimaryColor()).isEqualTo("#112233");
    }

    @Test
    void update_primaryColorOnly_changesOnlyThatField() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId, new TicketTemplateUpdateRequest(null, null, "#FEFEFE", null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.getPrimaryColor()).isEqualTo("#FEFEFE");
        assertThat(result.getLogoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(result.getBackgroundImageUrl()).isEqualTo("https://example.com/bg.png");
    }

    @Test
    void update_allFieldsNull_leavesTemplateUnchanged() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId, new TicketTemplateUpdateRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.getLogoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(result.getBackgroundImageUrl()).isEqualTo("https://example.com/bg.png");
        assertThat(result.getPrimaryColor()).isEqualTo("#112233");
    }

    // ---- code placement (QR / barcode) ----

    private TicketTemplateCreateRequest createRequestWithCode(CodeType type, Double x, Double y, Double width, Integer rotation) {
        return new TicketTemplateCreateRequest(null, TicketTemplateFormat.DIGITAL, null, null, null,
                type, x, y, width, rotation, null, null, null, null, null, null, null, null, null);
    }

    private void mockOwnerCreateAccess(UUID eventId) {
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        lenient().when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void create_withQrPlacement_storesIt_andDefaultsRotationToZero() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        TicketTemplate result = service.create(eventId, createRequestWithCode(CodeType.QR, 70.0, 55.0, 25.0, null));

        assertThat(result.getCodeType()).isEqualTo(CodeType.QR);
        assertThat(result.getCodeX()).isEqualTo(70.0);
        assertThat(result.getCodeY()).isEqualTo(55.0);
        assertThat(result.getCodeWidth()).isEqualTo(25.0);
        assertThat(result.getCodeRotation()).isZero();
    }

    @Test
    void create_withBarcodePlacement_keepsItsRotation() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        TicketTemplate result = service.create(eventId, createRequestWithCode(CodeType.BARCODE, 10.0, 20.0, 40.0, 90));

        assertThat(result.getCodeType()).isEqualTo(CodeType.BARCODE);
        assertThat(result.getCodeRotation()).isEqualTo(90);
    }

    @Test
    void create_withoutPlacement_leavesItUnset() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        TicketTemplate result = service.create(eventId, createRequestWithCode(null, null, null, null, null));

        assertThat(result.getCodeType()).isNull();
        assertThat(result.getCodeX()).isNull();
        assertThat(result.getCodeWidth()).isNull();
    }

    @Test
    void create_partialPlacement_throwsBadRequest_andSavesNothing() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createRequestWithCode(CodeType.QR, 10.0, null, 20.0, null)))
                .isInstanceOf(BadRequestException.class);
        verify(ticketTemplateRepository, never()).save(any());
    }

    @Test
    void create_codeOverflowingTheRightEdge_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createRequestWithCode(CodeType.QR, 80.0, 10.0, 25.0, null)))
                .isInstanceOf(BadRequestException.class);
        verify(ticketTemplateRepository, never()).save(any());
    }

    @Test
    void create_codeExactlyTouchingTheRightEdge_isAccepted() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThat(service.create(eventId, createRequestWithCode(CodeType.QR, 75.0, 10.0, 25.0, null)).getCodeX())
                .isEqualTo(75.0);
    }

    @Test
    void create_qrNarrowerThanItsScannableMinimum_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createRequestWithCode(CodeType.QR, 10.0, 10.0, 24.0, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("QR");
        assertThat(service.create(eventId, createRequestWithCode(CodeType.QR, 10.0, 10.0, 25.0, null)).getCodeWidth())
                .isEqualTo(25.0);
    }

    @Test
    void create_barcodeNarrowerThanItsScannableMinimum_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createRequestWithCode(CodeType.BARCODE, 10.0, 10.0, 30.0, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BARCODE");
    }

    @Test
    void create_rotatedQr_isAccepted() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        TicketTemplate result = service.create(eventId, createRequestWithCode(CodeType.QR, 10.0, 10.0, 30.0, 45));

        assertThat(result.getCodeType()).isEqualTo(CodeType.QR);
        assertThat(result.getCodeRotation()).isEqualTo(45);
    }

    @Test
    void create_rotationWithoutAPlacement_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createRequestWithCode(null, null, null, null, 90)))
                .isInstanceOf(BadRequestException.class);
    }

    private TicketTemplateUpdateRequest updateCode(CodeType type, Double x, Double y, Double width, Integer rotation) {
        return new TicketTemplateUpdateRequest(null, null, null, type, x, y, width, rotation, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void update_placementOnATemplateWithNone_requiresAllFour() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        assertThatThrownBy(() -> service.update(templateId, updateCode(null, 10.0, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(ticketTemplateRepository, never()).save(any());
    }

    @Test
    void update_fullPlacement_isStored() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId, updateCode(CodeType.BARCODE, 5.0, 60.0, 40.0, 270));

        assertThat(result.getCodeType()).isEqualTo(CodeType.BARCODE);
        assertThat(result.getCodeX()).isEqualTo(5.0);
        assertThat(result.getCodeY()).isEqualTo(60.0);
        assertThat(result.getCodeWidth()).isEqualTo(40.0);
        assertThat(result.getCodeRotation()).isEqualTo(270);
    }

    @Test
    void update_oneValue_isMergedWithTheStoredPlacement_andRevalidated() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        TicketTemplate existing = template(templateId, eventId);
        existing.setCodeType(CodeType.QR);
        existing.setCodeX(10.0);
        existing.setCodeY(10.0);
        existing.setCodeWidth(30.0);
        existing.setCodeRotation(0);
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(existing));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        lenient().when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketTemplate moved = service.update(templateId, updateCode(null, 40.0, null, null, null));
        assertThat(moved.getCodeX()).isEqualTo(40.0);
        assertThat(moved.getCodeWidth()).isEqualTo(30.0);

        // 90 + 20 > 100: fine on its own, invalid once merged with the stored width
        assertThatThrownBy(() -> service.update(templateId, updateCode(null, 90.0, null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_switchingBarcodeToQr_keepsTheStoredRotation() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        TicketTemplate existing = template(templateId, eventId);
        existing.setCodeType(CodeType.BARCODE);
        existing.setCodeX(10.0);
        existing.setCodeY(10.0);
        existing.setCodeWidth(40.0);
        existing.setCodeRotation(90);
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(existing));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        lenient().when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketTemplate result = service.update(templateId, updateCode(CodeType.QR, null, null, null, null));

        assertThat(result.getCodeType()).isEqualTo(CodeType.QR);
        assertThat(result.getCodeRotation()).isEqualTo(90);
    }

    @Test
    void update_blankBackgroundImageUrl_clearsIt() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId,
                new TicketTemplateUpdateRequest(null, "", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.getBackgroundImageUrl()).isNull();
    }
    // ---- ticket designer: canvas, background, text fields ----

    private TicketTextFieldDto field(TextFieldKey key) {
        return field(key, null, null, null, null);
    }

    private TicketTextFieldDto field(TextFieldKey key, Integer sampleLength, String sampleText, String text, List<Integer> lineBreaks) {
        return new TicketTextFieldDto(key, 10.0, 50.0, 8.0, "#112233", null, TextAlign.LEFT, null,
                sampleLength, sampleText, text, lineBreaks);
    }

    private TicketTemplateCreateRequest createWithFields(TicketTextFieldDto... fields) {
        return new TicketTemplateCreateRequest(null, TicketTemplateFormat.DIGITAL, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, List.of(fields));
    }

    private TicketTemplateCreateRequest createWithBackground(BackgroundFit fit, Double x, Double y, Double width, Double height) {
        return new TicketTemplateCreateRequest(null, TicketTemplateFormat.DIGITAL, null, null, null,
                null, null, null, null, null, null, null, null, fit, x, y, width, height, null);
    }

    private TicketTemplateUpdateRequest updateDesign(Integer ticketWidth, Integer ticketHeight, String backgroundColor,
                                                     BackgroundFit fit, Double x, Double y, Double width, Double height,
                                                     List<TicketTextFieldDto> fields) {
        return new TicketTemplateUpdateRequest(null, null, null, null, null, null, null, null,
                ticketWidth, ticketHeight, backgroundColor, fit, x, y, width, height, fields);
    }

    @Test
    void create_withTextFields_storesThemInOrder_withDefaults() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        TicketTemplate result = service.create(eventId, createWithFields(
                field(TextFieldKey.ATTENDEE_NAME, 12, null, null, null),
                field(TextFieldKey.EVENT_NAME),
                field(TextFieldKey.CUSTOM, null, null, "  Doors open at 7  ", List.of(5))));

        assertThat(result.getTextFields()).extracting(TicketTextField::getKey)
                .containsExactly(TextFieldKey.ATTENDEE_NAME, TextFieldKey.EVENT_NAME, TextFieldKey.CUSTOM);
        TicketTextField first = result.getTextFields().get(0);
        assertThat(first.isBold()).isFalse();
        assertThat(first.getRotation()).isZero();
        assertThat(first.getSampleLength()).isEqualTo(12);
        assertThat(result.getTextFields().get(2).getText()).isEqualTo("Doors open at 7");
        assertThat(result.getTextFields().get(2).getLineBreaks()).containsExactly(5);
    }

    @Test
    void create_withoutTextFields_hasAnEmptyList() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThat(service.create(eventId, createRequestWithCode(null, null, null, null, null)).getTextFields()).isEmpty();
    }

    @Test
    void create_aRepeatedNonCustomKey_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createWithFields(field(TextFieldKey.SEAT), field(TextFieldKey.SEAT))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("SEAT");
        verify(ticketTemplateRepository, never()).save(any());
    }

    @Test
    void create_upToTenCustomFields_areAllowed_andAnElevenththrows() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);
        TicketTextFieldDto[] ten = new TicketTextFieldDto[10];
        java.util.Arrays.fill(ten, field(TextFieldKey.CUSTOM, null, null, "hi", null));
        TicketTextFieldDto[] eleven = new TicketTextFieldDto[11];
        java.util.Arrays.fill(eleven, field(TextFieldKey.CUSTOM, null, null, "hi", null));

        assertThat(service.create(eventId, createWithFields(ten)).getTextFields()).hasSize(10);
        assertThatThrownBy(() -> service.create(eventId, createWithFields(eleven))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_customWithoutText_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createWithFields(field(TextFieldKey.CUSTOM))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(eventId, createWithFields(field(TextFieldKey.CUSTOM, null, null, "   ", null))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_textOnANonCustomKey_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createWithFields(field(TextFieldKey.EVENT_NAME, null, null, "hello", null))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_sampleTextOrLengthOnAStaticOrCustomKey_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createWithFields(field(TextFieldKey.EVENT_DATE, 5, null, null, null))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(eventId, createWithFields(field(TextFieldKey.VENUE, null, "Main Hall", null, null))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(eventId, createWithFields(field(TextFieldKey.CUSTOM, 5, null, "hi", null))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_aBlankSampleText_throwsBadRequest_andASampleTextIsTrimmed() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createWithFields(field(TextFieldKey.SEAT, null, "   ", null, null))))
                .isInstanceOf(BadRequestException.class);
        assertThat(service.create(eventId, createWithFields(field(TextFieldKey.SEAT, null, " 12A ", null, null)))
                .getTextFields().get(0).getSampleText()).isEqualTo("12A");
    }

    @Test
    void create_lineBreaksOnADynamicKey_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createWithFields(field(TextFieldKey.TICKET_NUMBER, null, null, null, List.of(3)))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("lineBreaks");
    }

    @Test
    void create_lineBreaksMustBeAscendingAndUnique() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createWithFields(field(TextFieldKey.CUSTOM, null, null, "abcdefghij", List.of(5, 3)))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(eventId, createWithFields(field(TextFieldKey.CUSTOM, null, null, "abcdefghij", List.of(3, 3)))))
                .isInstanceOf(BadRequestException.class);
        assertThat(service.create(eventId, createWithFields(field(TextFieldKey.CUSTOM, null, null, "abcdefghij", List.of(3, 5))))
                .getTextFields().get(0).getLineBreaks()).containsExactly(3, 5);
    }

    @Test
    void create_customLineBreakPastTheText_throwsBadRequest_butStaticKeysMayBreakAnywhere() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createWithFields(field(TextFieldKey.CUSTOM, null, null, "abcde", List.of(5)))))
                .isInstanceOf(BadRequestException.class);
        assertThat(service.create(eventId, createWithFields(field(TextFieldKey.CUSTOM, null, null, "abcde", List.of(4))))
                .getTextFields()).hasSize(1);
        // the event name's length is not known until a ticket is drawn, so any position is accepted
        assertThat(service.create(eventId, createWithFields(field(TextFieldKey.EVENT_NAME, null, null, null, List.of(40))))
                .getTextFields()).hasSize(1);
    }

    @Test
    void create_customBackground_needsItsWholeRectangle() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createWithBackground(BackgroundFit.CUSTOM, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(eventId, createWithBackground(BackgroundFit.CUSTOM, 0.0, 0.0, 100.0, null)))
                .isInstanceOf(BadRequestException.class);
        TicketTemplate ok = service.create(eventId, createWithBackground(BackgroundFit.CUSTOM, -20.0, -10.0, 150.0, 130.0));
        assertThat(ok.getBackgroundX()).isEqualTo(-20.0);
        assertThat(ok.getBackgroundWidth()).isEqualTo(150.0);
    }

    @Test
    void create_aRectangleWithAnyOtherFit_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        for (BackgroundFit fit : new BackgroundFit[] {BackgroundFit.COVER, BackgroundFit.CONTAIN, BackgroundFit.STRETCH, null}) {
            assertThatThrownBy(() -> service.create(eventId, createWithBackground(fit, 0.0, 0.0, 100.0, 100.0)))
                    .as("fit %s with a rectangle", fit).isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void create_nonCustomFits_withoutARectangle_areAccepted() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        for (BackgroundFit fit : new BackgroundFit[] {BackgroundFit.COVER, BackgroundFit.CONTAIN, BackgroundFit.STRETCH}) {
            assertThat(service.create(eventId, createWithBackground(fit, null, null, null, null)).getBackgroundFit()).isEqualTo(fit);
        }
    }

    @Test
    void create_codeTypeNone_withAPlacement_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createRequestWithCode(CodeType.NONE, 10.0, 10.0, 20.0, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(eventId, createRequestWithCode(CodeType.NONE, null, null, null, 90)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_codeTypeNone_alone_isAccepted_andHasNoPlacement() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        TicketTemplate result = service.create(eventId, createRequestWithCode(CodeType.NONE, null, null, null, null));

        assertThat(result.getCodeType()).isEqualTo(CodeType.NONE);
        assertThat(result.getCodeX()).isNull();
        assertThat(result.getCodeRotation()).isNull();
    }

    @Test
    void create_canvasAndColour_areStored_andABlankColourIsNull() {
        UUID eventId = UUID.randomUUID();
        mockOwnerCreateAccess(eventId);

        TicketTemplate result = service.create(eventId, new TicketTemplateCreateRequest(null, TicketTemplateFormat.DIGITAL,
                null, null, null, null, null, null, null, null, 1200, 500, "#ABCDEF", null, null, null, null, null, null));
        TicketTemplate blank = service.create(eventId, new TicketTemplateCreateRequest(null, TicketTemplateFormat.DIGITAL,
                null, null, null, null, null, null, null, null, null, null, "", null, null, null, null, null, null));

        assertThat(result.getTicketWidth()).isEqualTo(1200);
        assertThat(result.getTicketHeight()).isEqualTo(500);
        assertThat(result.getBackgroundColor()).isEqualTo("#ABCDEF");
        assertThat(blank.getBackgroundColor()).isNull();
    }

    // ---- ticket designer: PATCH merge semantics ----

    @Test
    void update_textFields_nullLeavesThemAlone_emptyClears_aListReplaces() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        TicketTemplate existing = template(templateId, eventId);
        existing.getTextFields().add(TicketTextField.builder().key(TextFieldKey.SEAT).x(1).y(2).fontSize(5).color("#000000")
                .align(TextAlign.LEFT).build());
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(existing));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        lenient().when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.update(templateId, updateDesign(null, null, null, null, null, null, null, null, null))
                .getTextFields()).hasSize(1);

        assertThat(service.update(templateId, updateDesign(null, null, null, null, null, null, null, null,
                List.of(field(TextFieldKey.EVENT_NAME), field(TextFieldKey.VENUE)))).getTextFields())
                .extracting(TicketTextField::getKey).containsExactly(TextFieldKey.EVENT_NAME, TextFieldKey.VENUE);

        assertThat(service.update(templateId, updateDesign(null, null, null, null, null, null, null, null, List.of()))
                .getTextFields()).isEmpty();
    }

    @Test
    void update_invalidTextFields_throwBadRequest_andNothingIsSaved() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        assertThatThrownBy(() -> service.update(templateId, updateDesign(null, null, null, null, null, null, null, null,
                List.of(field(TextFieldKey.ROW), field(TextFieldKey.ROW)))))
                .isInstanceOf(BadRequestException.class);
        verify(ticketTemplateRepository, never()).save(any());
    }

    @Test
    void update_canvasSizeAndBackgroundColour_areMerged_andABlankColourClearsIt() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        TicketTemplate result = service.update(templateId, updateDesign(1000, 400, "#102030", null, null, null, null, null, null));
        assertThat(result.getTicketWidth()).isEqualTo(1000);
        assertThat(result.getTicketHeight()).isEqualTo(400);
        assertThat(result.getBackgroundColor()).isEqualTo("#102030");

        TicketTemplate cleared = service.update(templateId, updateDesign(null, null, "", null, null, null, null, null, null));
        assertThat(cleared.getBackgroundColor()).isNull();
        assertThat(cleared.getTicketWidth()).isEqualTo(1000);
    }

    @Test
    void update_switchingFromCustomToAnotherFit_clearsTheStoredRectangle() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        TicketTemplate existing = template(templateId, eventId);
        existing.setBackgroundFit(BackgroundFit.CUSTOM);
        existing.setBackgroundX(-10.0);
        existing.setBackgroundY(-10.0);
        existing.setBackgroundWidth(120.0);
        existing.setBackgroundHeight(120.0);
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(existing));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        lenient().when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketTemplate result = service.update(templateId, updateDesign(null, null, null, BackgroundFit.COVER, null, null, null, null, null));

        assertThat(result.getBackgroundFit()).isEqualTo(BackgroundFit.COVER);
        assertThat(result.getBackgroundX()).isNull();
        assertThat(result.getBackgroundY()).isNull();
        assertThat(result.getBackgroundWidth()).isNull();
        assertThat(result.getBackgroundHeight()).isNull();
    }

    @Test
    void update_aRectangleTogetherWithANonCustomFit_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        assertThatThrownBy(() -> service.update(templateId,
                updateDesign(null, null, null, BackgroundFit.STRETCH, 0.0, 0.0, 100.0, 100.0, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_customFit_needsTheRectangleAcrossTheRequestAndTheStoredValues() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        assertThatThrownBy(() -> service.update(templateId,
                updateDesign(null, null, null, BackgroundFit.CUSTOM, 0.0, 0.0, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        TicketTemplate result = service.update(templateId,
                updateDesign(null, null, null, BackgroundFit.CUSTOM, 0.0, 0.0, 100.0, 100.0, null));
        assertThat(result.getBackgroundFit()).isEqualTo(BackgroundFit.CUSTOM);
    }

    @Test
    void update_codeTypeNone_clearsTheStoredPlacement() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        TicketTemplate existing = template(templateId, eventId);
        existing.setCodeType(CodeType.QR);
        existing.setCodeX(10.0);
        existing.setCodeY(10.0);
        existing.setCodeWidth(30.0);
        existing.setCodeRotation(45);
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(existing));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        lenient().when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketTemplate result = service.update(templateId, updateCode(CodeType.NONE, null, null, null, null));

        assertThat(result.getCodeType()).isEqualTo(CodeType.NONE);
        assertThat(result.getCodeX()).isNull();
        assertThat(result.getCodeY()).isNull();
        assertThat(result.getCodeWidth()).isNull();
        assertThat(result.getCodeRotation()).isNull();
    }

    @Test
    void update_codeTypeNone_withPlacementValues_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        assertThatThrownBy(() -> service.update(templateId, updateCode(CodeType.NONE, 10.0, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(templateId, updateCode(CodeType.NONE, null, null, null, 90)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_fromNoneBackToACode_needsAFullPlacement() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        TicketTemplate existing = template(templateId, eventId);
        existing.setCodeType(CodeType.NONE);
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(existing));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        lenient().when(ticketTemplateRepository.save(any(TicketTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.update(templateId, updateCode(CodeType.QR, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        TicketTemplate result = service.update(templateId, updateCode(CodeType.QR, 10.0, 10.0, 30.0, 0));
        assertThat(result.getCodeType()).isEqualTo(CodeType.QR);
        assertThat(result.getCodeWidth()).isEqualTo(30.0);
    }
    // ---- delete() ----

    @Test
    void delete_owner_softDeletesTheTemplate() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        mockOwnerAccess(templateId, eventId);

        service.delete(templateId);

        org.mockito.ArgumentCaptor<TicketTemplate> saved = org.mockito.ArgumentCaptor.forClass(TicketTemplate.class);
        verify(ticketTemplateRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(templateId);
        assertThat(saved.getValue().getDeletedAt()).isNotNull();
        verify(ticketTemplateRepository, never()).delete(any());
        verify(ticketTemplateRepository, never()).deleteById(any());
    }

    @Test
    void delete_organizer_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template(templateId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);

        service.delete(templateId);

        verify(ticketTemplateRepository).save(any(TicketTemplate.class));
    }

    @Test
    void delete_admin_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(template(templateId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(true);

        service.delete(templateId);

        verify(ticketTemplateRepository).save(any(TicketTemplate.class));
    }

    @Test
    void delete_stranger_throwsForbidden_andDeletesNothing() {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        TicketTemplate existing = template(templateId, eventId);
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.of(existing));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(templateId)).isInstanceOf(ForbiddenException.class);

        verify(ticketTemplateRepository, never()).save(any());
        assertThat(existing.getDeletedAt()).isNull();
    }

    @Test
    void delete_unknownOrAlreadyDeletedTemplate_throwsResourceNotFound() {
        UUID templateId = UUID.randomUUID();
        when(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(templateId)).isInstanceOf(ResourceNotFoundException.class);
        verify(ticketTemplateRepository, never()).save(any());
    }
}
