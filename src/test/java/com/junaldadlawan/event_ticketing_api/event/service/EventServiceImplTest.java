package com.junaldadlawan.event_ticketing_api.event.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.dto.EventRequest;
import com.junaldadlawan.event_ticketing_api.event.dto.EventUpdateRequest;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link EventServiceImpl} (no Spring context),
 * mirroring {@code VenueServiceImplTest}'s style. Covers BR-EVENT-001/002,
 * BR-TICKET-001-005 and BR-AUTH-004 as implemented in this Phase-3 hardening
 * pass — see {@code testing/event-hardening-test-results.md} for the full
 * scenario map.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    private EventServiceImpl eventService;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        eventService = new EventServiceImpl(eventRepository, organizationRepository, venueRepository, accessGuard);
        orgId = UUID.randomUUID();
    }

    private Organization organization(UUID id, OrganizationStatus status) {
        return Organization.builder().id(id).name("Org").status(status).build();
    }

    private Venue venue(UUID id, UUID organizationId) {
        return Venue.builder().id(id).organizationId(organizationId).name("Venue").build();
    }

    private Event event(UUID id, UUID organizationId, EventStatus status) {
        return Event.builder()
                .id(id)
                .organizationId(organizationId)
                .title("Original Title")
                .description("Original Description")
                .category("music")
                .status(status)
                .ticketPrefix("ABC")
                .startAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(10, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
    }

    private EventRequest createRequest(UUID organizationId, UUID venueId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant endAt = startAt.plus(2, ChronoUnit.HOURS);
        return new EventRequest(organizationId, "Title", "Description", "music", venueId,
                startAt, endAt, "UTC", null);
    }

    // ---- createEvent() ----

    @Test
    void createEvent_owner_succeeds() {
        UUID ownerId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(eventRepository.existsByTicketPrefix(anyString())).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = eventService.createEvent(createRequest(orgId, null));

        assertThat(result.getOrganizationId()).isEqualTo(orgId);
        assertThat(result.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(result.getTicketPrefix()).matches("[A-Z]{3}");
    }

    @Test
    void createEvent_organizer_succeeds() {
        UUID organizerId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        when(eventRepository.existsByTicketPrefix(anyString())).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = eventService.createEvent(createRequest(orgId, null));

        assertThat(result.getOrganizationId()).isEqualTo(orgId);
    }

    /**
     * BR-AUTH-004: the just-fixed HIGH finding — an admin can create an event
     * for an APPROVED organization with NO role in that org at all. The
     * access guard's org-role check must never even be consulted.
     */
    @Test
    void createEvent_adminWithNoOrgRole_bypassesOrgRoleCheck() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(eventRepository.existsByTicketPrefix(anyString())).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = eventService.createEvent(createRequest(orgId, null));

        assertThat(result.getOrganizationId()).isEqualTo(orgId);
        // The admin bypass must short-circuit before ever asking about roles.
        verify(accessGuard, never()).currentUserId();
        verify(accessGuard, never()).hasRole(any(), any(), any());
    }

    @Test
    void createEvent_nonExistentOrganization_throwsResourceNotFound() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.createEvent(createRequest(orgId, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(accessGuard);
    }

    @Test
    void createEvent_organizationNotApproved_throwsForbidden_regardlessOfCaller() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.PENDING)));

        assertThatThrownBy(() -> eventService.createEvent(createRequest(orgId, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).save(any());
        // The approval check happens before any caller/role check is even performed.
        verifyNoInteractions(accessGuard);
    }

    /**
     * Confirms the org-approval gate applies even to an admin caller — the
     * admin bypass is scoped to the org-role check only, not the org's own
     * approval status.
     */
    @Test
    void createEvent_organizationNotApproved_rejectsEvenAdmin() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.PENDING)));

        assertThatThrownBy(() -> eventService.createEvent(createRequest(orgId, null)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(accessGuard);
    }

    @Test
    void createEvent_nonOwnerNonOrganizer_throwsForbidden() {
        UUID strangerId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> eventService.createEvent(createRequest(orgId, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).save(any());
    }

    /**
     * The key regression this phase fixes: a caller who owns/organizes a
     * DIFFERENT organization (not just a stranger with no role anywhere)
     * must still be forbidden from creating an event for {@code orgId}.
     */
    @Test
    void createEvent_ownerOfDifferentOrganization_throwsForbidden() {
        UUID otherOrgId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> eventService.createEvent(createRequest(orgId, null)))
                .isInstanceOf(ForbiddenException.class);
        // The guard must have been asked about the TARGET org, never the caller's own other org.
        verify(accessGuard).hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER);
        verify(accessGuard, never()).hasRole(otherOrgOwnerId, otherOrgId, OrganizationRole.OWNER);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void createEvent_venueFromDifferentOrganization_throwsBadRequest() {
        UUID ownerId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue(venueId, otherOrgId)));

        assertThatThrownBy(() -> eventService.createEvent(createRequest(orgId, venueId)))
                .isInstanceOf(BadRequestException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void createEvent_nonExistentVenue_throwsResourceNotFound() {
        UUID ownerId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.createEvent(createRequest(orgId, venueId)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void createEvent_venueFromSameOrganization_succeeds() {
        UUID ownerId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue(venueId, orgId)));
        when(eventRepository.existsByTicketPrefix(anyString())).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = eventService.createEvent(createRequest(orgId, venueId));

        assertThat(result.getVenueId()).isEqualTo(venueId);
    }

    /**
     * BR-TICKET-003/004: the prefix is server-generated and its
     * uniqueness is enforced via a retry loop against
     * {@code existsByTicketPrefix} — never client-supplied (there is no
     * such field on {@link EventRequest} to begin with).
     */
    @Test
    void createEvent_ticketPrefixCollision_retriesUntilUnique() {
        UUID ownerId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        // First two candidates collide, third is free.
        when(eventRepository.existsByTicketPrefix(anyString())).thenReturn(true, true, false);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = eventService.createEvent(createRequest(orgId, null));

        assertThat(result.getTicketPrefix()).matches("[A-Z]{3}");
        verify(eventRepository, times(3)).existsByTicketPrefix(anyString());
    }

    @Test
    void createEvent_allCandidatesCollide_throwsIllegalState() {
        UUID ownerId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(eventRepository.existsByTicketPrefix(anyString())).thenReturn(true);

        assertThatThrownBy(() -> eventService.createEvent(createRequest(orgId, null)))
                .isInstanceOf(IllegalStateException.class);
        verify(eventRepository, never()).save(any());
        verify(eventRepository, atLeast(50)).existsByTicketPrefix(anyString());
    }

    // ---- getEvent() ----

    @Test
    void getEvent_publishedStatus_succeedsWithoutTouchingAccessGuard() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.PUBLISHED)));

        Event result = eventService.getEvent(eventId);

        assertThat(result.getId()).isEqualTo(eventId);
        verifyNoInteractions(accessGuard);
    }

    @Test
    void getEvent_draftStatus_nonPrivilegedCaller_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> eventService.getEvent(eventId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getEvent_draftStatus_ownerOfEventsOrg_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);

        Event result = eventService.getEvent(eventId);

        assertThat(result.getId()).isEqualTo(eventId);
    }

    @Test
    void getEvent_draftStatus_admin_succeeds() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(true);

        Event result = eventService.getEvent(eventId);

        assertThat(result.getId()).isEqualTo(eventId);
        verify(accessGuard, never()).currentUserId();
    }

    @Test
    void getEvent_draftStatus_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> eventService.getEvent(eventId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getEvent_unknownId_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEvent(eventId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard);
    }

    // ---- updateEvent() : partial-update matrix ----

    private void mockOwnerAccess(UUID eventId, EventStatus status) {
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, status)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        // lenient: not every caller of this helper exercises the save path
        // (e.g. blank-field-rejection and conflict-status tests never reach save()).
        org.mockito.Mockito.lenient().when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateEvent_titleOnly_changesOnlyTitle() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, EventStatus.DRAFT);

        Event result = eventService.updateEvent(eventId, new EventUpdateRequest("New Title", null, null, null));

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getDescription()).isEqualTo("Original Description");
        assertThat(result.getCategory()).isEqualTo("music");
    }

    @Test
    void updateEvent_descriptionOnly_changesOnlyDescription() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, EventStatus.DRAFT);

        Event result = eventService.updateEvent(eventId, new EventUpdateRequest(null, "New Description", null, null));

        assertThat(result.getDescription()).isEqualTo("New Description");
        assertThat(result.getTitle()).isEqualTo("Original Title");
    }

    @Test
    void updateEvent_categoryOnly_changesOnlyCategory() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, EventStatus.DRAFT);

        Event result = eventService.updateEvent(eventId, new EventUpdateRequest(null, null, "sports", null));

        assertThat(result.getCategory()).isEqualTo("sports");
        assertThat(result.getTitle()).isEqualTo("Original Title");
    }

    @Test
    void updateEvent_imagesOnly_changesOnlyImages() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, EventStatus.DRAFT);

        Event result = eventService.updateEvent(eventId,
                new EventUpdateRequest(null, null, null, List.of("https://example.com/a.jpg")));

        assertThat(result.getImages()).containsExactly("https://example.com/a.jpg");
        assertThat(result.getTitle()).isEqualTo("Original Title");
    }

    @Test
    void updateEvent_allFieldsOmitted_leavesEventUnchanged() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, EventStatus.DRAFT);

        Event result = eventService.updateEvent(eventId, new EventUpdateRequest(null, null, null, null));

        assertThat(result.getTitle()).isEqualTo("Original Title");
        assertThat(result.getDescription()).isEqualTo("Original Description");
        assertThat(result.getCategory()).isEqualTo("music");
    }

    @Test
    void updateEvent_blankTitle_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, EventStatus.DRAFT);

        assertThatThrownBy(() -> eventService.updateEvent(eventId, new EventUpdateRequest("   ", null, null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateEvent_blankDescription_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, EventStatus.DRAFT);

        assertThatThrownBy(() -> eventService.updateEvent(eventId, new EventUpdateRequest(null, "   ", null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateEvent_blankCategory_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, EventStatus.DRAFT);

        assertThatThrownBy(() -> eventService.updateEvent(eventId, new EventUpdateRequest(null, null, "   ", null)))
                .isInstanceOf(BadRequestException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateEvent_nonOwnerNonOrganizer_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> eventService.updateEvent(eventId, new EventUpdateRequest("New Title", null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).save(any());
    }

    /**
     * Core regression fix of this phase: {@code EventUpdateRequest} has no
     * organization field at all — the check must always resolve from the
     * persisted event's own {@code organizationId}. A caller who owns/organizes
     * a DIFFERENT org must still be forbidden.
     */
    @Test
    void updateEvent_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> eventService.updateEvent(eventId, new EventUpdateRequest("Hijacked", null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(accessGuard).hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER);
        verify(accessGuard, never()).hasRole(otherOrgOwnerId, otherOrgId, OrganizationRole.OWNER);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateEvent_admin_succeedsWithNoOrgRole() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = eventService.updateEvent(eventId, new EventUpdateRequest("Renamed By Admin", null, null, null));

        assertThat(result.getTitle()).isEqualTo("Renamed By Admin");
        verify(accessGuard, never()).currentUserId();
    }

    @Test
    void updateEvent_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.updateEvent(eventId, new EventUpdateRequest("New Title", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard);
    }

    // ---- publishEvent() : status-transition matrix ----

    @Test
    void publishEvent_draftAndOrgApproved_succeeds() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, EventStatus.DRAFT);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED)));

        Event result = eventService.publishEvent(eventId);

        assertThat(result.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void publishEvent_organizationNotApproved_throwsConflict() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, EventStatus.DRAFT);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.PENDING)));

        assertThatThrownBy(() -> eventService.publishEvent(eventId))
                .isInstanceOf(ConflictException.class);
        verify(eventRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"PUBLISHED", "ON_SALE", "SOLD_OUT", "CANCELLED", "COMPLETED"})
    void publishEvent_nonDraftStatus_throwsConflict(EventStatus currentStatus) {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, currentStatus);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(orgId, OrganizationStatus.APPROVED)));

        assertThatThrownBy(() -> eventService.publishEvent(eventId))
                .isInstanceOf(ConflictException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void publishEvent_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> eventService.publishEvent(eventId))
                .isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).save(any());
    }

    // ---- cancelEvent() : status-transition matrix ----

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"DRAFT", "PUBLISHED", "ON_SALE", "SOLD_OUT"})
    void cancelEvent_cancellableStatus_succeeds(EventStatus currentStatus) {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, currentStatus);

        Event result = eventService.cancelEvent(eventId);

        assertThat(result.getStatus()).isEqualTo(EventStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"CANCELLED", "COMPLETED"})
    void cancelEvent_nonCancellableStatus_throwsConflict(EventStatus currentStatus) {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, currentStatus);

        assertThatThrownBy(() -> eventService.cancelEvent(eventId))
                .isInstanceOf(ConflictException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void cancelEvent_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.PUBLISHED)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> eventService.cancelEvent(eventId))
                .isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void cancelEvent_admin_succeedsWithNoOrgRole() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.PUBLISHED)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event result = eventService.cancelEvent(eventId);

        assertThat(result.getStatus()).isEqualTo(EventStatus.CANCELLED);
        verify(accessGuard, never()).currentUserId();
    }

    // ---- delete() : proving the second core fix (previously NO per-event auth check at all) ----

    @Test
    void delete_owner_succeeds() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId, EventStatus.DRAFT);

        eventService.delete(eventId);

        verify(eventRepository).save(argThatDeleted());
    }

    private Event argThatDeleted() {
        return org.mockito.ArgumentMatchers.argThat(e -> e != null && e.getDeletedAt() != null);
    }

    /**
     * The second core fix of this phase: {@code delete} previously had NO
     * per-event authorization check at all. A caller who owns/organizes a
     * DIFFERENT organization must now be forbidden.
     */
    @Test
    void delete_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> eventService.delete(eventId))
                .isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void delete_nonOwnerNonOrganizer_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> eventService.delete(eventId))
                .isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void delete_admin_succeedsWithNoOrgRole() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        eventService.delete(eventId);

        verify(accessGuard, never()).currentUserId();
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void delete_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.delete(eventId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard);
    }

    // ---- resolveVenue() ----

    @Test
    void resolveVenue_nullVenueId_returnsNull() {
        assertThat(eventService.resolveVenue(null)).isNull();
        verifyNoInteractions(venueRepository);
    }

    @Test
    void resolveVenue_existingVenue_returnsVenue() {
        UUID venueId = UUID.randomUUID();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue(venueId, orgId)));

        Venue result = eventService.resolveVenue(venueId);

        assertThat(result.getId()).isEqualTo(venueId);
    }

    @Test
    void resolveVenue_unknownVenue_returnsNull() {
        UUID venueId = UUID.randomUUID();
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        assertThat(eventService.resolveVenue(venueId)).isNull();
    }
}
