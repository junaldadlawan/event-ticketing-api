package com.junaldadlawan.event_ticketing_api.venue.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.venue.dto.VenueCreateRequest;
import com.junaldadlawan.event_ticketing_api.venue.dto.VenueUpdateRequest;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Mockito unit tests for {@link VenueServiceImpl} (no Spring context),
 * mirroring {@code OrganizationServiceImplTest}'s style. Covers the
 * openapi.yaml-authoritative behavior spec for the Venue module (no
 * BR-VENUE-* / UC-VENUE-* docs exist yet):
 * {@code /organizations/{orgId}/venues} (create/list, ~openapi.yaml lines
 * 367-395) and {@code /venues/{venueId}} (get/update, ~lines 397-428).
 */
@ExtendWith(MockitoExtension.class)
class VenueServiceImplTest {

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    private VenueServiceImpl venueService;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        venueService = new VenueServiceImpl(venueRepository, organizationRepository, accessGuard);
        orgId = UUID.randomUUID();
    }

    private Venue venue(UUID venueId, UUID organizationId) {
        return Venue.builder()
                .id(venueId)
                .organizationId(organizationId)
                .name("Original Name")
                .address("Original Address")
                .latitude(1.0)
                .longitude(2.0)
                .build();
    }

    // ---- create() ----

    @Test
    void create_owner_succeeds() {
        UUID ownerId = UUID.randomUUID();
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VenueCreateRequest request = new VenueCreateRequest("Main Hall", "123 Main St", 1.1, 2.2);
        Venue result = venueService.create(orgId, request);

        assertThat(result.getOrganizationId()).isEqualTo(orgId);
        assertThat(result.getName()).isEqualTo("Main Hall");
        assertThat(result.getAddress()).isEqualTo("123 Main St");
        assertThat(result.getLatitude()).isEqualTo(1.1);
        assertThat(result.getLongitude()).isEqualTo(2.2);
    }

    @Test
    void create_organizer_succeeds() {
        UUID organizerId = UUID.randomUUID();
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VenueCreateRequest request = new VenueCreateRequest("Main Hall", null, null, null);
        Venue result = venueService.create(orgId, request);

        assertThat(result.getName()).isEqualTo("Main Hall");
        assertThat(result.getAddress()).isNull();
    }

    @Test
    void create_virtualVenue_noAddress_succeeds() {
        UUID ownerId = UUID.randomUUID();
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VenueCreateRequest request = new VenueCreateRequest("Virtual Stage", null, null, null);
        Venue result = venueService.create(orgId, request);

        assertThat(result.getAddress()).isNull();
        assertThat(result.getLatitude()).isNull();
        assertThat(result.getLongitude()).isNull();
    }

    @Test
    void create_nonOwnerNonOrganizer_throwsForbidden() {
        UUID checkInStaffId = UUID.randomUUID();
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(checkInStaffId);
        when(accessGuard.hasRole(checkInStaffId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(checkInStaffId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        VenueCreateRequest request = new VenueCreateRequest("Main Hall", null, null, null);

        assertThatThrownBy(() -> venueService.create(orgId, request))
                .isInstanceOf(ForbiddenException.class);
        verify(venueRepository, never()).save(any());
    }

    @Test
    void create_nonExistentOrganization_throwsResourceNotFound() {
        when(organizationRepository.existsById(orgId)).thenReturn(false);

        VenueCreateRequest request = new VenueCreateRequest("Main Hall", null, null, null);

        assertThatThrownBy(() -> venueService.create(orgId, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(venueRepository, never()).save(any());
        // Must fail fast on the missing org before ever touching the access guard.
        verifyNoInteractions(accessGuard);
    }

    // ---- list() ----

    @Test
    void list_member_succeeds() {
        UUID memberId = UUID.randomUUID();
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(memberId);
        when(accessGuard.isMember(memberId, orgId)).thenReturn(true);
        when(venueRepository.findByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(List.of(venue(UUID.randomUUID(), orgId)));

        List<Venue> result = venueService.list(orgId);

        assertThat(result).hasSize(1);
    }

    @Test
    void list_admin_succeeds() {
        UUID adminId = UUID.randomUUID();
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(adminId);
        when(accessGuard.isMember(adminId, orgId)).thenReturn(false);
        when(accessGuard.isAdmin()).thenReturn(true);
        when(venueRepository.findByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(List.of());

        List<Venue> result = venueService.list(orgId);

        assertThat(result).isEmpty();
    }

    @Test
    void list_nonMember_throwsForbidden() {
        UUID strangerId = UUID.randomUUID();
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.isMember(strangerId, orgId)).thenReturn(false);
        when(accessGuard.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> venueService.list(orgId))
                .isInstanceOf(ForbiddenException.class);
        verify(venueRepository, never()).findByOrganizationIdAndDeletedAtIsNull(any());
    }

    @Test
    void list_nonExistentOrganization_throwsResourceNotFound() {
        when(organizationRepository.existsById(orgId)).thenReturn(false);

        assertThatThrownBy(() -> venueService.list(orgId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard);
    }

    // ---- get() : no auth check at all, per openapi.yaml `security: []` ----

    @Test
    void get_noSecurityContext_succeedsWithoutTouchingAccessGuard() {
        UUID venueId = UUID.randomUUID();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue(venueId, orgId)));

        Venue result = venueService.get(venueId);

        assertThat(result.getId()).isEqualTo(venueId);
        // The whole point of this test: get() must never call into
        // OrganizationAccessGuard, since a real caller may have no
        // Authorization header (and thus no SecurityContext) at all.
        verifyNoInteractions(accessGuard);
    }

    @Test
    void get_softDeletedVenue_throwsResourceNotFound() {
        UUID venueId = UUID.randomUUID();
        Venue deleted = venue(venueId, orgId);
        deleted.markDeleted();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> venueService.get(venueId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_unknownId_throwsResourceNotFound() {
        UUID venueId = UUID.randomUUID();
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueService.get(venueId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard);
    }

    // ---- update() : partial-update matrix ----

    @Test
    void update_nameOnly_changesOnlyName() {
        UUID venueId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Venue existing = venue(venueId, orgId);
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(existing));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Venue result = venueService.update(venueId, new VenueUpdateRequest("New Name", null, null, null));

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getAddress()).isEqualTo("Original Address");
        assertThat(result.getLatitude()).isEqualTo(1.0);
        assertThat(result.getLongitude()).isEqualTo(2.0);
    }

    @Test
    void update_addressOnly_changesOnlyAddress() {
        UUID venueId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Venue existing = venue(venueId, orgId);
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(existing));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Venue result = venueService.update(venueId, new VenueUpdateRequest(null, "New Address", null, null));

        assertThat(result.getName()).isEqualTo("Original Name");
        assertThat(result.getAddress()).isEqualTo("New Address");
    }

    @Test
    void update_allFieldsOmitted_leavesVenueUnchanged() {
        UUID venueId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        Venue existing = venue(venueId, orgId);
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(existing));
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Venue result = venueService.update(venueId, new VenueUpdateRequest(null, null, null, null));

        assertThat(result.getName()).isEqualTo("Original Name");
        assertThat(result.getAddress()).isEqualTo("Original Address");
        assertThat(result.getLatitude()).isEqualTo(1.0);
        assertThat(result.getLongitude()).isEqualTo(2.0);
    }

    @Test
    void update_blankName_throwsBadRequest() {
        UUID venueId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Venue existing = venue(venueId, orgId);
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(existing));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);

        assertThatThrownBy(() -> venueService.update(venueId, new VenueUpdateRequest("   ", null, null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(venueRepository, never()).save(any());
    }

    @Test
    void update_nonOwnerNonOrganizerOfVenuesOrg_throwsForbidden() {
        UUID venueId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        Venue existing = venue(venueId, orgId);
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(existing));
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> venueService.update(venueId, new VenueUpdateRequest("New Name", null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(venueRepository, never()).save(any());
    }

    @Test
    void update_unknownVenue_throwsResourceNotFound() {
        UUID venueId = UUID.randomUUID();
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueService.update(venueId, new VenueUpdateRequest("New Name", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard);
    }

    /**
     * {@link VenueUpdateRequest} has no organization-id field at all — confirms
     * the authorization check is always resolved from the persisted venue's own
     * {@code organizationId}, not any client-supplied value. A caller who is
     * owner/organizer of some *other* organization (not the venue's own) must
     * still be forbidden, proving the guard is invoked with
     * {@code venue.getOrganizationId()} rather than anything the request could
     * influence.
     */
    @Test
    void update_authorizationAlwaysResolvedFromPersistedVenuesOrganization_notClientInput() {
        UUID venueId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();
        UUID ownerOfOtherOrgId = UUID.randomUUID();
        Venue existing = venue(venueId, orgId);
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(existing));
        when(accessGuard.currentUserId()).thenReturn(ownerOfOtherOrgId);
        // Caller owns a DIFFERENT org, not this venue's org.
        when(accessGuard.hasRole(ownerOfOtherOrgId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(ownerOfOtherOrgId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> venueService.update(venueId, new VenueUpdateRequest("New Name", null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        // The guard must have been asked about the venue's own org (orgId), never otherOrgId.
        verify(accessGuard).hasRole(ownerOfOtherOrgId, orgId, OrganizationRole.OWNER);
        verify(accessGuard, never()).hasRole(ownerOfOtherOrgId, otherOrgId, OrganizationRole.OWNER);
        verify(venueRepository, never()).save(any());
    }
}
