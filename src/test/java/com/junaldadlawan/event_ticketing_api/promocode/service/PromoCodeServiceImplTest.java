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
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeCreateRequest;
import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import com.junaldadlawan.event_ticketing_api.promocode.enums.PromoCodeStatus;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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
import static org.mockito.Mockito.lenient;
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
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private OrderRepository orderRepository;

    private PromoCodeServiceImpl service;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new PromoCodeServiceImpl(promoCodeRepository, eventRepository, accessGuard, ticketTypeRepository, orderRepository);
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
    // ---- update() / setStatus() / delete() / usedCount() ----

    private PromoCode promoCode(UUID id, UUID eventId) {
        return PromoCode.builder().id(id).eventId(eventId).code("SAVE10").discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10)).usageLimitTotal(100).usageLimitPerBuyer(2)
                .validFrom(Instant.parse("2026-10-01T00:00:00Z")).validUntil(Instant.parse("2026-11-01T00:00:00Z")).build();
    }

    /** Owner of the event's organization, the promo code exists, nothing has used it yet. */
    private PromoCode ownedPromoCode(UUID id, UUID eventId) {
        PromoCode existing = promoCode(id, eventId);
        UUID ownerId = UUID.randomUUID();
        when(promoCodeRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(existing));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        lenient().when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return existing;
    }

    private void usedBy(PromoCode promoCode, long uses) {
        when(orderRepository.countPromoCodeUses(promoCode.getCode(), promoCode.getEventId())).thenReturn(uses);
    }

    private PromoCodeUpdateRequest patch(String code, DiscountType type, BigDecimal value, Set<UUID> ticketTypes,
                                         Integer limitTotal, Integer limitPerBuyer, Instant from, Instant until) {
        return new PromoCodeUpdateRequest(code, type, value, ticketTypes, limitTotal, limitPerBuyer, from, until);
    }

    private PromoCodeUpdateRequest nothing() {
        return patch(null, null, null, null, null, null, null, null);
    }

    private void ticketTypeOf(UUID ticketTypeId, UUID eventId) {
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(
                TicketType.builder().id(ticketTypeId).eventId(eventId).build()));
    }

    // -- update --

    @Test
    void update_changesOnlyTheFieldsSent() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PromoCode existing = ownedPromoCode(id, eventId);

        PromoCode result = service.update(id, patch(null, null, BigDecimal.valueOf(25), null, 50, null, null, null));

        assertThat(result.getDiscountValue()).isEqualByComparingTo("25");
        assertThat(result.getUsageLimitTotal()).isEqualTo(50);
        assertThat(result.getCode()).isEqualTo("SAVE10");
        assertThat(result.getUsageLimitPerBuyer()).isEqualTo(2);
        assertThat(result.getValidFrom()).isEqualTo(existing.getValidFrom());
        verify(promoCodeRepository).save(existing);
    }

    @Test
    void update_withEveryFieldOmitted_leavesThePromoCodeUnchanged() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PromoCode existing = ownedPromoCode(id, eventId);

        PromoCode result = service.update(id, nothing());

        assertThat(result.getCode()).isEqualTo("SAVE10");
        assertThat(result.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(result.getDiscountValue()).isEqualByComparingTo("10");
        assertThat(result.getUsageLimitTotal()).isEqualTo(100);
        assertThat(result.getUsageLimitPerBuyer()).isEqualTo(2);
        assertThat(result.getValidUntil()).isEqualTo(existing.getValidUntil());
    }

    @Test
    void update_zeroLimitsRemoveTheLimits() {
        UUID id = UUID.randomUUID();
        ownedPromoCode(id, UUID.randomUUID());

        PromoCode result = service.update(id, patch(null, null, null, null, 0, 0, null, null));

        assertThat(result.getUsageLimitTotal()).isNull();
        assertThat(result.getUsageLimitPerBuyer()).isNull();
    }

    @Test
    void update_anEmptyTicketTypeSetAppliesToEveryTicketType_andASetReplacesTheOldOne() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PromoCode existing = ownedPromoCode(id, eventId);
        existing.getApplicableTicketTypeIds().add(UUID.randomUUID());
        UUID vip = UUID.randomUUID();
        UUID ga = UUID.randomUUID();
        ticketTypeOf(vip, eventId);
        ticketTypeOf(ga, eventId);

        assertThat(service.update(id, patch(null, null, null, Set.of(vip, ga), null, null, null, null))
                .getApplicableTicketTypeIds()).containsExactlyInAnyOrder(vip, ga);
        assertThat(service.update(id, patch(null, null, null, Set.of(), null, null, null, null))
                .getApplicableTicketTypeIds()).isEmpty();
    }

    @Test
    void update_aTicketTypeOfAnotherEventOrAnUnknownOne_throwsBadRequest_andSavesNothing() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ownedPromoCode(id, eventId);
        UUID foreign = UUID.randomUUID();
        ticketTypeOf(foreign, UUID.randomUUID());
        UUID unknown = UUID.randomUUID();
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, patch(null, null, null, Set.of(foreign), null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(id, patch(null, null, null, Set.of(unknown), null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void update_theMergedValidityWindowMustBeValid() {
        UUID id = UUID.randomUUID();
        ownedPromoCode(id, UUID.randomUUID());

        // only validUntil is sent, before the stored validFrom
        assertThatThrownBy(() -> service.update(id, patch(null, null, null, null, null, null, null, Instant.parse("2026-09-01T00:00:00Z"))))
                .isInstanceOf(BadRequestException.class);
        // only validFrom is sent, after the stored validUntil
        assertThatThrownBy(() -> service.update(id, patch(null, null, null, null, null, null, Instant.parse("2026-12-01T00:00:00Z"), null)))
                .isInstanceOf(BadRequestException.class);
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void update_aPercentageOver100_isRejected_evenWhenOnlyTheTypeOrOnlyTheValueChanges() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PromoCode existing = ownedPromoCode(id, eventId);

        assertThatThrownBy(() -> service.update(id, patch(null, null, BigDecimal.valueOf(101), null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);

        existing.setDiscountType(DiscountType.FIXED);
        existing.setDiscountValue(BigDecimal.valueOf(500));
        assertThatThrownBy(() -> service.update(id, patch(null, DiscountType.PERCENTAGE, null, null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void update_aNewCode_isSaved_andTheSameCodeResentIsNotAConflict() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PromoCode existing = ownedPromoCode(id, eventId);
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SUMMER")).thenReturn(Optional.empty());

        assertThat(service.update(id, patch("SUMMER", null, null, null, null, null, null, null)).getCode()).isEqualTo("SUMMER");

        existing.setCode("SUMMER");
        // re-sending the code it already has is not a change, so there is nothing to conflict with
        assertThat(service.update(id, patch("SUMMER", null, null, null, null, null, null, null)).getCode()).isEqualTo("SUMMER");
    }

    @Test
    void update_aCodeAnotherPromoCodeOfTheEventAlreadyHas_throwsConflict_withAReadableMessage() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ownedPromoCode(id, eventId);
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "TAKEN"))
                .thenReturn(Optional.of(promoCode(UUID.randomUUID(), eventId)));

        assertThatThrownBy(() -> service.update(id, patch("TAKEN", null, null, null, null, null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("TAKEN")
                .hasMessageContaining("already exists");
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void update_aConcurrentDuplicateThatSlipsPastTheCheck_isStillA409_notA500() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ownedPromoCode(id, eventId);
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "RACE")).thenReturn(Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class))).thenThrow(new DataIntegrityViolationException("uq_promo_codes_event_code_active"));

        assertThatThrownBy(() -> service.update(id, patch("RACE", null, null, null, null, null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_aBlankCode_throwsBadRequest() {
        UUID id = UUID.randomUUID();
        ownedPromoCode(id, UUID.randomUUID());

        assertThatThrownBy(() -> service.update(id, patch("   ", null, null, null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_onceUsed_theCodeAndTheDiscountTypeCannotChange() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PromoCode existing = ownedPromoCode(id, eventId);
        usedBy(existing, 3);

        assertThatThrownBy(() -> service.update(id, patch("NEWCODE", null, null, null, null, null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been used")
                .hasMessageContaining("pause it and create a new one");
        assertThatThrownBy(() -> service.update(id, patch(null, DiscountType.FIXED, null, null, null, null, null, null)))
                .isInstanceOf(ConflictException.class);
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void update_onceUsed_theOtherFieldsCanStillChange_andTheSameCodeAndTypeMayBeResent() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PromoCode existing = ownedPromoCode(id, eventId);
        usedBy(existing, 3);

        PromoCode result = service.update(id, patch("SAVE10", DiscountType.PERCENTAGE, BigDecimal.valueOf(15), null, 200, 5,
                Instant.parse("2026-10-02T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z")));

        assertThat(result.getDiscountValue()).isEqualByComparingTo("15");
        assertThat(result.getUsageLimitTotal()).isEqualTo(200);
        assertThat(result.getUsageLimitPerBuyer()).isEqualTo(5);
        assertThat(result.getValidUntil()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    void update_aTotalLimitBelowTheUsesIsRejected_theUsesThemselvesAreAccepted_andZeroMeansNoLimit() {
        UUID id = UUID.randomUUID();
        PromoCode existing = ownedPromoCode(id, UUID.randomUUID());
        usedBy(existing, 5);

        assertThatThrownBy(() -> service.update(id, patch(null, null, null, null, 4, null, null, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("5");
        assertThat(service.update(id, patch(null, null, null, null, 5, null, null, null)).getUsageLimitTotal()).isEqualTo(5);
        assertThat(service.update(id, patch(null, null, null, null, 0, null, null, null)).getUsageLimitTotal()).isNull();
    }

    @Test
    void update_organizerAndAdmin_areAllowed() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        when(promoCodeRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(promoCode(id, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        lenient().when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.update(id, nothing())).isNotNull();

        when(accessGuard.isAdmin()).thenReturn(true);
        assertThat(service.update(id, nothing())).isNotNull();
    }

    @Test
    void update_strangerOrCrossOrgOwner_throwsForbidden_resolvedFromThePromoCodesOwnEvent() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(promoCodeRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(promoCode(id, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.update(id, nothing())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.setStatus(id, PromoCodeStatus.PAUSED)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ForbiddenException.class);
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void updateStatusAndDelete_unknownOrDeletedPromoCode_throwResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(promoCodeRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, nothing())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.setStatus(id, PromoCodeStatus.PAUSED)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    // -- status --

    @Test
    void setStatus_pausesAndResumes() {
        UUID id = UUID.randomUUID();
        PromoCode existing = ownedPromoCode(id, UUID.randomUUID());

        assertThat(service.setStatus(id, PromoCodeStatus.PAUSED).isPaused()).isTrue();
        assertThat(service.setStatus(id, PromoCodeStatus.ACTIVE).isPaused()).isFalse();
        verify(promoCodeRepository, org.mockito.Mockito.times(2)).save(existing);
    }

    @Test
    void setStatus_theStateItAlreadyHas_isNotAnError_andWritesNothing() {
        UUID id = UUID.randomUUID();
        PromoCode existing = ownedPromoCode(id, UUID.randomUUID());

        assertThat(service.setStatus(id, PromoCodeStatus.ACTIVE).isPaused()).isFalse();
        existing.setPaused(true);
        assertThat(service.setStatus(id, PromoCodeStatus.PAUSED).isPaused()).isTrue();

        verify(promoCodeRepository, never()).save(any());
    }

    // -- delete --

    @Test
    void delete_anUnusedPromoCode_isSoftDeleted() {
        UUID id = UUID.randomUUID();
        PromoCode existing = ownedPromoCode(id, UUID.randomUUID());
        usedBy(existing, 0);

        service.delete(id);

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(promoCodeRepository).save(existing);
        verify(promoCodeRepository, never()).delete(any());
        verify(promoCodeRepository, never()).deleteById(any());
    }

    @Test
    void delete_aUsedPromoCode_throwsConflict_andDeletesNothing() {
        UUID id = UUID.randomUUID();
        PromoCode existing = ownedPromoCode(id, UUID.randomUUID());
        usedBy(existing, 1);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("can't be deleted")
                .hasMessageContaining("Pause it instead");

        assertThat(existing.getDeletedAt()).isNull();
        verify(promoCodeRepository, never()).save(any());
    }

    // -- usedCount --

    @Test
    void usedCount_isTheNumberOfNonCancelledOrdersThatUsedTheCodeForItsEvent() {
        UUID eventId = UUID.randomUUID();
        PromoCode existing = promoCode(UUID.randomUUID(), eventId);
        when(orderRepository.countPromoCodeUses("SAVE10", eventId)).thenReturn(7L);

        assertThat(service.usedCount(existing)).isEqualTo(7);
    }

    @Test
    void create_aDuplicateThatSlipsPastTheCheck_isA409() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(eventId, createRequest(DiscountType.PERCENTAGE, BigDecimal.TEN,
                Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)))).isInstanceOf(ConflictException.class);
    }
}
