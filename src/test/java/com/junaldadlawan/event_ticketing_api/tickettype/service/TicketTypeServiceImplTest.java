package com.junaldadlawan.event_ticketing_api.tickettype.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
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
 * Mockito unit tests for {@link TicketTypeServiceImpl} (no Spring context),
 * mirroring {@code EventServiceImplTest}/{@code VenueServiceImplTest}'s
 * style. Covers BR-EVENT-003 and BR-AUTH-004 as implemented in Phase 4 — see
 * {@code testing/ticket-type-seatmap-test-results.md} for the full scenario
 * map.
 */
@ExtendWith(MockitoExtension.class)
class TicketTypeServiceImplTest {

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    private TicketTypeServiceImpl service;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new TicketTypeServiceImpl(ticketTypeRepository, eventRepository, accessGuard);
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

    private TicketType ticketType(UUID id, UUID eventId, int quantityTotal, int quantityAvailable) {
        Instant saleStartAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(5, ChronoUnit.DAYS);
        return TicketType.builder()
                .id(id)
                .eventId(eventId)
                .name("General Admission")
                .kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .quantityTotal(quantityTotal)
                .quantityAvailable(quantityAvailable)
                .saleStartAt(saleStartAt)
                .saleEndAt(saleEndAt)
                .maxPerOrder(10)
                .build();
    }

    private TicketTypeCreateRequest createRequest(Integer maxPerOrder) {
        Instant saleStartAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(5, ChronoUnit.DAYS);
        return new TicketTypeCreateRequest(
                "General Admission",
                TicketTypeKind.GENERAL_ADMISSION,
                new MoneyDto(1000L, "USD"),
                100,
                saleStartAt,
                saleEndAt,
                maxPerOrder);
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
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketType result = service.create(eventId, createRequest(null));

        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getName()).isEqualTo("General Admission");
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
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketType result = service.create(eventId, createRequest(null));

        assertThat(result.getEventId()).isEqualTo(eventId);
    }

    /** BR-AUTH-004: admin bypass must short-circuit before any org-role lookup. */
    @Test
    void create_adminWithNoOrgRole_bypassesOrgRoleCheck() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketType result = service.create(eventId, createRequest(null));

        assertThat(result.getEventId()).isEqualTo(eventId);
        verify(accessGuard, never()).currentUserId();
        verify(accessGuard, never()).hasRole(any(), any(), any());
    }

    @Test
    void create_nonExistentEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(eventId, createRequest(null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ticketTypeRepository, never()).save(any());
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
        verify(ticketTypeRepository, never()).save(any());
    }

    /**
     * The same regression class as Phase 3: an owner/organizer of a
     * DIFFERENT organization must still be forbidden, not just a roleless
     * stranger.
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
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void create_saleEndAtBeforeSaleStartAt_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        Instant saleStartAt = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant saleEndAt = Instant.now().plus(1, ChronoUnit.DAYS);
        TicketTypeCreateRequest request = new TicketTypeCreateRequest(
                "GA", TicketTypeKind.GENERAL_ADMISSION, new MoneyDto(1000L, "USD"), 100, saleStartAt, saleEndAt, null);

        assertThatThrownBy(() -> service.create(eventId, request))
                .isInstanceOf(BadRequestException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void create_saleEndAtEqualsSaleStartAt_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        Instant sameInstant = Instant.now().plus(3, ChronoUnit.DAYS);
        TicketTypeCreateRequest request = new TicketTypeCreateRequest(
                "GA", TicketTypeKind.GENERAL_ADMISSION, new MoneyDto(1000L, "USD"), 100, sameInstant, sameInstant, null);

        assertThatThrownBy(() -> service.create(eventId, request))
                .isInstanceOf(BadRequestException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void create_quantityAvailableInitializedToQuantityTotal() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketType result = service.create(eventId, createRequest(null));

        assertThat(result.getQuantityTotal()).isEqualTo(100);
        assertThat(result.getQuantityAvailable()).isEqualTo(100);
    }

    @Test
    void create_maxPerOrderOmitted_defaultsToTen() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketType result = service.create(eventId, createRequest(null));

        assertThat(result.getMaxPerOrder()).isEqualTo(10);
    }

    @Test
    void create_maxPerOrderProvided_usesProvidedValue() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketType result = service.create(eventId, createRequest(4));

        assertThat(result.getMaxPerOrder()).isEqualTo(4);
    }

    // ---- list() ----

    @Test
    void list_publishedEvent_succeedsWithoutTouchingAccessGuard() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.PUBLISHED)));
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(List.of(ticketType(UUID.randomUUID(), eventId, 100, 100)));

        List<TicketType> result = service.list(eventId);

        assertThat(result).hasSize(1);
        verifyNoInteractions(accessGuard);
    }

    @Test
    void list_draftEvent_stranger_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.list(eventId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void list_draftEvent_ownerOfEventsOrg_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(List.of());

        List<TicketType> result = service.list(eventId);

        assertThat(result).isEmpty();
    }

    @Test
    void list_draftEvent_admin_succeeds() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(List.of());

        service.list(eventId);

        verify(accessGuard, never()).currentUserId();
    }

    @Test
    void list_draftEvent_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.list(eventId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void list_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(eventId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard);
    }

    // ---- get() ----

    @Test
    void get_publishedEvent_succeedsWithoutTouchingAccessGuard() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 100, 100)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.PUBLISHED)));

        TicketType result = service.get(ticketTypeId);

        assertThat(result.getId()).isEqualTo(ticketTypeId);
        verifyNoInteractions(accessGuard);
    }

    @Test
    void get_draftEvent_stranger_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 100, 100)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.get(ticketTypeId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void get_draftEvent_ownerOfEventsOrg_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 100, 100)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);

        TicketType result = service.get(ticketTypeId);

        assertThat(result.getId()).isEqualTo(ticketTypeId);
    }

    @Test
    void get_draftEvent_admin_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 100, 100)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(true);

        service.get(ticketTypeId);

        verify(accessGuard, never()).currentUserId();
    }

    @Test
    void get_draftEvent_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 100, 100)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.get(ticketTypeId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void get_unknownTicketType_throwsResourceNotFound() {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ticketTypeId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard);
    }

    // ---- update() : authorization ----

    private void mockOwnerAccess(UUID ticketTypeId, UUID eventId, int quantityTotal, int quantityAvailable) {
        UUID ownerId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, quantityTotal, quantityAvailable)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        lenient().when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void update_owner_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);

        TicketType result = service.update(ticketTypeId, new TicketTypeUpdateRequest("Renamed", null, null, null, null, null));

        assertThat(result.getName()).isEqualTo("Renamed");
    }

    @Test
    void update_unknownTicketType_throwsResourceNotFound() {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(ticketTypeId, new TicketTypeUpdateRequest("X", null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard);
    }

    @Test
    void update_stranger_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 100, 100)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.update(ticketTypeId, new TicketTypeUpdateRequest("Hijacked", null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    /** Same cross-org regression class as create/list/get, on the mutation path. */
    @Test
    void update_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 100, 100)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.update(ticketTypeId, new TicketTypeUpdateRequest("Hijacked", null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(accessGuard).hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER);
        verify(accessGuard, never()).hasRole(otherOrgOwnerId, otherOrgId, OrganizationRole.OWNER);
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void update_admin_succeedsWithNoOrgRole() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 100, 100)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketType result = service.update(ticketTypeId, new TicketTypeUpdateRequest("Renamed By Admin", null, null, null, null, null));

        assertThat(result.getName()).isEqualTo("Renamed By Admin");
        verify(accessGuard, never()).currentUserId();
    }

    // ---- update() : partial-update matrix ----

    @Test
    void update_nameOnly_changesOnlyName() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);

        TicketType result = service.update(ticketTypeId, new TicketTypeUpdateRequest("New Name", null, null, null, null, null));

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getPrice().getAmount()).isEqualTo(1000L);
        assertThat(result.getQuantityTotal()).isEqualTo(100);
    }

    @Test
    void update_blankName_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);

        assertThatThrownBy(() -> service.update(ticketTypeId, new TicketTypeUpdateRequest("   ", null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void update_priceOnly_changesOnlyPrice() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);

        TicketType result = service.update(ticketTypeId, new TicketTypeUpdateRequest(null, new MoneyDto(2500L, "EUR"), null, null, null, null));

        assertThat(result.getPrice().getAmount()).isEqualTo(2500L);
        assertThat(result.getPrice().getCurrency()).isEqualTo("EUR");
        assertThat(result.getName()).isEqualTo("General Admission");
    }

    @Test
    void update_maxPerOrderOnly_changesOnlyMaxPerOrder() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);

        TicketType result = service.update(ticketTypeId, new TicketTypeUpdateRequest(null, null, null, null, null, 6));

        assertThat(result.getMaxPerOrder()).isEqualTo(6);
        assertThat(result.getName()).isEqualTo("General Admission");
    }

    @Test
    void update_allFieldsOmitted_leavesTicketTypeUnchanged() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);

        TicketType result = service.update(ticketTypeId, new TicketTypeUpdateRequest(null, null, null, null, null, null));

        assertThat(result.getName()).isEqualTo("General Admission");
        assertThat(result.getQuantityTotal()).isEqualTo(100);
        assertThat(result.getQuantityAvailable()).isEqualTo(100);
        assertThat(result.getMaxPerOrder()).isEqualTo(10);
    }

    /**
     * The dedicated edge case code-reviewer specifically checked:
     * {@code quantityAvailable} resyncs to the new {@code quantityTotal}
     * ONLY when {@code quantityTotal} is present in the request.
     */
    @Test
    void update_quantityTotalPresent_resyncsQuantityAvailable() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        // Simulate a ticket type whose quantityAvailable has already drifted
        // from quantityTotal (e.g. via a future Phase-5 decrement).
        mockOwnerAccess(ticketTypeId, eventId, 100, 40);

        TicketType result = service.update(ticketTypeId, new TicketTypeUpdateRequest(null, null, 150, null, null, null));

        assertThat(result.getQuantityTotal()).isEqualTo(150);
        assertThat(result.getQuantityAvailable()).isEqualTo(150);
    }

    @Test
    void update_quantityTotalOmitted_leavesQuantityAvailableUntouched() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        // quantityAvailable (40) has already diverged from quantityTotal (100).
        mockOwnerAccess(ticketTypeId, eventId, 100, 40);

        TicketType result = service.update(ticketTypeId, new TicketTypeUpdateRequest("Renamed", null, null, null, null, null));

        assertThat(result.getQuantityTotal()).isEqualTo(100);
        // The key edge case: omitting quantityTotal must NOT resync quantityAvailable.
        assertThat(result.getQuantityAvailable()).isEqualTo(40);
    }

    // ---- update() : sale-window validation re-checked against merged final state ----

    @Test
    void update_saleEndAtOnly_movedBeforeExistingSaleStartAt_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);
        // Existing saleStartAt is now()+1d; move saleEndAt to now()+12h (before it),
        // without touching saleStartAt at all.
        Instant newSaleEndAt = Instant.now().plus(12, ChronoUnit.HOURS);

        assertThatThrownBy(() -> service.update(ticketTypeId, new TicketTypeUpdateRequest(null, null, null, null, newSaleEndAt, null)))
                .isInstanceOf(BadRequestException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void update_saleStartAtOnly_movedAfterExistingSaleEndAt_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);
        // Existing saleEndAt is now()+5d; move saleStartAt to now()+6d (after it),
        // without touching saleEndAt at all.
        Instant newSaleStartAt = Instant.now().plus(6, ChronoUnit.DAYS);

        assertThatThrownBy(() -> service.update(ticketTypeId, new TicketTypeUpdateRequest(null, null, null, newSaleStartAt, null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void update_bothSaleWindowFields_consistentNewWindow_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);
        Instant newSaleStartAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant newSaleEndAt = Instant.now().plus(20, ChronoUnit.DAYS);

        TicketType result = service.update(ticketTypeId, new TicketTypeUpdateRequest(null, null, null, newSaleStartAt, newSaleEndAt, null));

        assertThat(result.getSaleStartAt()).isEqualTo(newSaleStartAt);
        assertThat(result.getSaleEndAt()).isEqualTo(newSaleEndAt);
    }
}
