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
import com.junaldadlawan.event_ticketing_api.tickettype.enums.SalesStatus;
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

    @Mock
    private com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository ticketRepository;

    @Mock
    private com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository cartItemRepository;

    private TicketTypeServiceImpl service;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new TicketTypeServiceImpl(ticketTypeRepository, eventRepository, accessGuard, ticketRepository, cartItemRepository);
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
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(eventId)).thenReturn(List.of(ticketType(UUID.randomUUID(), eventId, 100, 100)));

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
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(eventId)).thenReturn(List.of());

        List<TicketType> result = service.list(eventId);

        assertThat(result).isEmpty();
    }

    @Test
    void list_draftEvent_admin_succeeds() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(eventId)).thenReturn(List.of());

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

    // ---- delete() / pause sales ----

    @Test
    void delete_ownerWithNoSales_softDeletes() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);
        when(cartItemRepository.existsByTicketTypeId(ticketTypeId)).thenReturn(false);
        when(ticketRepository.existsByTicketTypeId(ticketTypeId)).thenReturn(false);

        service.delete(ticketTypeId);

        org.mockito.ArgumentCaptor<TicketType> saved = org.mockito.ArgumentCaptor.forClass(TicketType.class);
        verify(ticketTypeRepository).save(saved.capture());
        assertThat(saved.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void delete_withSoldTickets_throwsConflict() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 97); // 3 sold or held

        assertThatThrownBy(() -> service.delete(ticketTypeId))
                .isInstanceOf(com.junaldadlawan.event_ticketing_api.common.exception.ConflictException.class)
                .hasMessageContaining("Pause its sales");
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void delete_withExistingTicketRows_throwsConflict() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);
        when(cartItemRepository.existsByTicketTypeId(ticketTypeId)).thenReturn(false);
        when(ticketRepository.existsByTicketTypeId(ticketTypeId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(ticketTypeId))
                .isInstanceOf(com.junaldadlawan.event_ticketing_api.common.exception.ConflictException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void delete_unknownTicketType_throwsResourceNotFound() {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ticketTypeId)).isInstanceOf(ResourceNotFoundException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void delete_stranger_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 100, 100)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(ticketTypeId)).isInstanceOf(ForbiddenException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void update_pauseAndResume_togglesSalesPaused() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);

        TicketType paused = service.update(ticketTypeId, new TicketTypeUpdateRequest(null, null, null, null, null, null, true));
        assertThat(paused.isSalesPaused()).isTrue();

        TicketType resumed = service.update(ticketTypeId, new TicketTypeUpdateRequest(null, null, null, null, null, null, false));
        assertThat(resumed.isSalesPaused()).isFalse();
    }

    @Test
    void update_withoutSalesPausedField_leavesItAlone() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);

        TicketType result = service.update(ticketTypeId, new TicketTypeUpdateRequest("Renamed", null, null, null, null, null));

        assertThat(result.isSalesPaused()).isFalse();
    }

    // ---- setSalesStatus() ----

    @Test
    void setSalesStatus_paused_owner_setsThePausedFlag_andSaves() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        mockOwnerAccess(ticketTypeId, eventId, 100, 100);

        TicketType result = service.setSalesStatus(ticketTypeId, SalesStatus.PAUSED);

        assertThat(result.isSalesPaused()).isTrue();
        verify(ticketTypeRepository).save(result);
    }

    @Test
    void setSalesStatus_active_owner_clearsThePausedFlag_andSaves() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType paused = ticketType(ticketTypeId, eventId, 100, 100);
        paused.setSalesPaused(true);
        UUID ownerId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(paused));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        lenient().when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketType result = service.setSalesStatus(ticketTypeId, SalesStatus.ACTIVE);

        assertThat(result.isSalesPaused()).isFalse();
        verify(ticketTypeRepository).save(paused);
    }

    @Test
    void setSalesStatus_paused_whenAlreadyPaused_isANoOp_withNoWrite() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        TicketType paused = ticketType(ticketTypeId, eventId, 100, 100);
        paused.setSalesPaused(true);
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(paused));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);

        assertThat(service.setSalesStatus(ticketTypeId, SalesStatus.PAUSED).isSalesPaused()).isTrue();

        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void setSalesStatus_active_whenNotPaused_isANoOp_withNoWrite() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 100, 100)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);

        assertThat(service.setSalesStatus(ticketTypeId, SalesStatus.ACTIVE).isSalesPaused()).isFalse();

        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void setSalesStatus_paused_organizerAndAdmin_areAllowed() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 100, 100)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        lenient().when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.setSalesStatus(ticketTypeId, SalesStatus.PAUSED).isSalesPaused()).isTrue();

        UUID otherTypeId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(otherTypeId)).thenReturn(Optional.of(ticketType(otherTypeId, eventId, 100, 100)));
        when(accessGuard.isAdmin()).thenReturn(true);
        assertThat(service.setSalesStatus(otherTypeId, SalesStatus.PAUSED).isSalesPaused()).isTrue();
    }

    @Test
    void setSalesStatus_workInAnyEventStatus() {
        for (EventStatus status : EventStatus.values()) {
            UUID eventId = UUID.randomUUID();
            UUID ticketTypeId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 100, 100)));
            when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, status)));
            when(accessGuard.isAdmin()).thenReturn(false);
            when(accessGuard.currentUserId()).thenReturn(ownerId);
            when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
            lenient().when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(service.setSalesStatus(ticketTypeId, SalesStatus.PAUSED).isSalesPaused()).as("pause in %s", status).isTrue();
        }
    }

    @Test
    void setSalesStatus_stranger_throwForbidden_andSaveNothing() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        TicketType existing = ticketType(ticketTypeId, eventId, 100, 100);
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(existing));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.setSalesStatus(ticketTypeId, SalesStatus.PAUSED)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.setSalesStatus(ticketTypeId, SalesStatus.ACTIVE)).isInstanceOf(ForbiddenException.class);

        verify(ticketTypeRepository, never()).save(any());
        assertThat(existing.isSalesPaused()).isFalse();
    }

    @Test
    void setSalesStatus_unknownOrDeletedTicketType_throwResourceNotFound() {
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setSalesStatus(ticketTypeId, SalesStatus.PAUSED)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.setSalesStatus(ticketTypeId, SalesStatus.ACTIVE)).isInstanceOf(ResourceNotFoundException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    // ---- position / reorder() ----

    private void mockOwnerOfEvent(UUID eventId) {
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
    }

    private TicketType typeAt(UUID id, UUID eventId, int position) {
        TicketType ticketType = ticketType(id, eventId, 100, 100);
        ticketType.setPosition(position);
        return ticketType;
    }

    @Test
    void create_putsTheNewTicketTypeAtTheEndOfTheArrangement() {
        UUID eventId = UUID.randomUUID();
        mockOwnerOfEvent(eventId);
        when(ticketTypeRepository.findMaxPositionByEventId(eventId)).thenReturn(4);
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(eventId, createRequest(null)).getPosition()).isEqualTo(5);
    }

    @Test
    void create_theFirstTicketTypeOfAnEvent_isPositionZero() {
        UUID eventId = UUID.randomUUID();
        mockOwnerOfEvent(eventId);
        when(ticketTypeRepository.findMaxPositionByEventId(eventId)).thenReturn(-1);
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(eventId, createRequest(null)).getPosition()).isZero();
    }

    @Test
    void reorder_savesTheGivenOrderAsPositionsZeroToN() {
        UUID eventId = UUID.randomUUID();
        mockOwnerOfEvent(eventId);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(eventId))
                .thenReturn(List.of(typeAt(a, eventId, 0), typeAt(b, eventId, 1), typeAt(c, eventId, 2)));
        when(ticketTypeRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<TicketType> result = service.reorder(eventId, List.of(c, a, b));

        assertThat(result).extracting(TicketType::getId).containsExactly(c, a, b);
        assertThat(result).extracting(TicketType::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    void reorder_aGapLeftByADelete_isClosedUp() {
        UUID eventId = UUID.randomUUID();
        mockOwnerOfEvent(eventId);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(eventId))
                .thenReturn(List.of(typeAt(a, eventId, 0), typeAt(b, eventId, 5)));
        when(ticketTypeRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.reorder(eventId, List.of(a, b))).extracting(TicketType::getPosition).containsExactly(0, 1);
    }

    @Test
    void reorder_aListThatOmitsATicketType_throwsBadRequest_andSavesNothing() {
        UUID eventId = UUID.randomUUID();
        mockOwnerOfEvent(eventId);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(eventId))
                .thenReturn(List.of(typeAt(a, eventId, 0), typeAt(b, eventId, 1)));

        assertThatThrownBy(() -> service.reorder(eventId, List.of(a))).isInstanceOf(BadRequestException.class);
        verify(ticketTypeRepository, never()).saveAll(any());
    }

    @Test
    void reorder_aListWithAnUnknownOrForeignId_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerOfEvent(eventId);
        UUID a = UUID.randomUUID();
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(eventId))
                .thenReturn(List.of(typeAt(a, eventId, 0)));

        assertThatThrownBy(() -> service.reorder(eventId, List.of(a, UUID.randomUUID()))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.reorder(eventId, List.of(UUID.randomUUID()))).isInstanceOf(BadRequestException.class);
        verify(ticketTypeRepository, never()).saveAll(any());
    }

    @Test
    void reorder_aTicketTypeListedTwice_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerOfEvent(eventId);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThatThrownBy(() -> service.reorder(eventId, List.of(a, a, b))).isInstanceOf(BadRequestException.class);
        verify(ticketTypeRepository, never()).saveAll(any());
    }

    @Test
    void reorder_stranger_throwsForbidden_andSavesNothing() {
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.reorder(eventId, List.of(UUID.randomUUID()))).isInstanceOf(ForbiddenException.class);
        verify(ticketTypeRepository, never()).saveAll(any());
    }

    @Test
    void reorder_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reorder(eventId, List.of(UUID.randomUUID()))).isInstanceOf(ResourceNotFoundException.class);
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
