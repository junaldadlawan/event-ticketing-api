package com.junaldadlawan.event_ticketing_api.promocode.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeCreateRequest;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link PromoCodeServiceImpl} (no Spring context),
 * mirroring {@code TicketTypeServiceImplTest}'s style. Covers BR-PROMO-001,
 * BR-AUTH-004, and UC-EVENT-06 as implemented in Phase 5a.
 */
@ExtendWith(MockitoExtension.class)
class PromoCodeServiceImplTest {

    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private PromoCodeServiceImpl service;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new PromoCodeServiceImpl(promoCodeRepository, eventRepository, accessGuard);
        orgId = UUID.randomUUID();
    }

    private Event event(UUID id, UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return Event.builder()
                .id(id)
                .organizationId(organizationId)
                .title("Concert")
                .description("desc")
                .category("music")
                .status(EventStatus.PUBLISHED)
                .ticketPrefix("ABC")
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
    }

    private PromoCodeCreateRequest createRequest(DiscountType type, BigDecimal value, Instant validFrom, Instant validUntil) {
        return new PromoCodeCreateRequest("SAVE10", type, value, Set.of(), null, null, validFrom, validUntil);
    }

    // ---- create() : authorization ----

    @Test
    void create_owner_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PromoCode result = service.create(eventId, createRequest(DiscountType.PERCENTAGE, BigDecimal.valueOf(10),
                Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)));

        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getCode()).isEqualTo("SAVE10");
    }

    @Test
    void create_organizer_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PromoCode result = service.create(eventId, createRequest(DiscountType.PERCENTAGE, BigDecimal.valueOf(10),
                Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)));

        assertThat(result.getEventId()).isEqualTo(eventId);
    }

    /** BR-AUTH-004: admin bypass must short-circuit before any org-role lookup. */
    @Test
    void create_adminWithNoOrgRole_bypassesOrgRoleCheck() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(eventId, createRequest(DiscountType.PERCENTAGE, BigDecimal.valueOf(10),
                Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)));

        verify(accessGuard, never()).currentUserId();
        verify(accessGuard, never()).hasRole(any(), any(), any());
    }

    @Test
    void create_nonExistentEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(eventId, createRequest(DiscountType.PERCENTAGE, BigDecimal.valueOf(10),
                Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(promoCodeRepository, never()).save(any());
        verify(accessGuard, never()).isAdmin();
    }

    @Test
    void create_stranger_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.create(eventId, createRequest(DiscountType.PERCENTAGE, BigDecimal.valueOf(10),
                Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(ForbiddenException.class);
        verify(promoCodeRepository, never()).save(any());
    }

    /** The Phase 3/4 regression class: an owner/organizer of a DIFFERENT org must still be forbidden. */
    @Test
    void create_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.create(eventId, createRequest(DiscountType.PERCENTAGE, BigDecimal.valueOf(10),
                Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(ForbiddenException.class);
        verify(promoCodeRepository, never()).save(any());
    }

    // ---- create() : validation ----

    private void mockOwnerAccess(UUID eventId) {
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
    }

    @Test
    void create_validUntilEqualsValidFrom_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId);
        Instant sameInstant = Instant.now().plus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> service.create(eventId, createRequest(DiscountType.PERCENTAGE, BigDecimal.valueOf(10), sameInstant, sameInstant)))
                .isInstanceOf(BadRequestException.class);
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void create_validUntilBeforeValidFrom_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId);
        Instant validFrom = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant validUntil = Instant.now().plus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> service.create(eventId, createRequest(DiscountType.PERCENTAGE, BigDecimal.valueOf(10), validFrom, validUntil)))
                .isInstanceOf(BadRequestException.class);
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void create_percentageDiscountValueOver100_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId);

        assertThatThrownBy(() -> service.create(eventId, createRequest(DiscountType.PERCENTAGE, BigDecimal.valueOf(101),
                Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(BadRequestException.class);
        verify(promoCodeRepository, never()).save(any());
    }

    /** Boundary: discountValue == 100 for PERCENTAGE must be ACCEPTED, not rejected. */
    @Test
    void create_percentageDiscountValueExactly100_isAccepted() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId);
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PromoCode result = service.create(eventId, createRequest(DiscountType.PERCENTAGE, BigDecimal.valueOf(100),
                Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)));

        assertThat(result.getDiscountValue()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void create_fixedDiscountValueOver100_isAccepted_noUpperBoundForFixed() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId);
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PromoCode result = service.create(eventId, createRequest(DiscountType.FIXED, BigDecimal.valueOf(5000),
                Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)));

        assertThat(result.getDiscountValue()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    void create_duplicateCodeForSameEvent_throwsConflict() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId);
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10"))
                .thenReturn(Optional.of(PromoCode.builder().id(UUID.randomUUID()).eventId(eventId).code("SAVE10").build()));

        assertThatThrownBy(() -> service.create(eventId, createRequest(DiscountType.PERCENTAGE, BigDecimal.valueOf(10),
                Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(ConflictException.class);
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void create_applicableTicketTypeIdsOmitted_defaultsToEmptySet() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId);
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PromoCodeCreateRequest request = new PromoCodeCreateRequest("SAVE10", DiscountType.PERCENTAGE, BigDecimal.valueOf(10),
                null, null, null, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS));

        PromoCode result = service.create(eventId, request);

        assertThat(result.getApplicableTicketTypeIds()).isEmpty();
    }

    // ---- list() ----

    @Test
    void list_owner_succeeds() {
        UUID eventId = UUID.randomUUID();
        mockOwnerAccess(eventId);
        when(promoCodeRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(List.of());

        List<PromoCode> result = service.list(eventId);

        assertThat(result).isEmpty();
    }

    @Test
    void list_admin_succeeds() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(promoCodeRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(List.of());

        service.list(eventId);

        verify(accessGuard, never()).currentUserId();
    }

    /**
     * Key regression vs. TicketType's draft-based public visibility: promo-code
     * listing is owner/organizer/admin ONLY, ALWAYS — even for a published event,
     * a stranger must be forbidden, not silently allowed once the event is public.
     */
    @Test
    void list_strangerOnPublishedEvent_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.list(eventId)).isInstanceOf(ForbiddenException.class);
        verify(promoCodeRepository, never()).findByEventIdAndDeletedAtIsNull(any());
    }

    @Test
    void list_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
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
        verify(accessGuard, never()).isAdmin();
    }
}
