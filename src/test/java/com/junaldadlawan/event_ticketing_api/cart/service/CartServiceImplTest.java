package com.junaldadlawan.event_ticketing_api.cart.service;

import com.junaldadlawan.event_ticketing_api.cart.dto.CartItemCreateRequest;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartResponse;
import com.junaldadlawan.event_ticketing_api.cart.entity.Cart;
import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.exception.UnprocessableEntityException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.promocode.service.PromoCodeUsageLimitGuard;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatMapRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link CartServiceImpl} (no Spring context, no real
 * DB), mirroring {@code TicketTypeServiceImplTest}'s style. Covers the
 * business-rule logic that's meaningfully unit-testable with mocked
 * repositories: authorization (buyer-only, no admin/organizer bypass),
 * purchasability/sale-window gating, single-event-per-cart, hold placement
 * and release, and promo-code application/discount math (BR-CART-001,
 * BR-PROMO-001/003/004/005/007, BR-INV-003/004, UC-ATTND-02/03).
 * <p>
 * The actual pessimistic row locking and concurrent-request serialization
 * (BR-INV-005/006) cannot be meaningfully proven with mocked repositories —
 * that's covered by real-DB concurrent requests in
 * {@code CartAccessIntegrationTest}. {@code EntityManager.refresh()} is a
 * mocked no-op here since there's no real persistence context to refresh
 * from; the tests instead mutate the same in-memory {@link TicketType}
 * instance the mocked {@code findByIdForUpdate} returns, which is exactly
 * what {@code refresh()} would functionally cause against a real DB.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private SeatMapRepository seatMapRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private PromoCodeUsageLimitGuard promoCodeUsageLimitGuard;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private EntityManager entityManager;

    private CartServiceImpl service;

    private UUID buyerId;

    @BeforeEach
    void setUp() {
        service = new CartServiceImpl(
                cartRepository, cartItemRepository, ticketTypeRepository, seatRepository,
                seatMapRepository, eventRepository, promoCodeRepository, promoCodeUsageLimitGuard, accessGuard, entityManager);
        buyerId = UUID.randomUUID();
    }

    // ---- fixtures ----

    private Cart cart(UUID id, UUID buyerId) {
        return Cart.builder().id(id).buyerId(buyerId).createdAt(Instant.now()).build();
    }

    private Event event(UUID id, EventStatus status) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return Event.builder()
                .id(id)
                .organizationId(UUID.randomUUID())
                .title("Concert")
                .description("desc")
                .category("music")
                .status(status)
                .ticketPrefix("ABC")
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
    }

    private TicketType gaTicketType(UUID id, UUID eventId, int quantityAvailable) {
        return TicketType.builder()
                .id(id)
                .eventId(eventId)
                .name("GA")
                .kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .quantityTotal(100)
                .quantityAvailable(quantityAvailable)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10)
                .build();
    }

    private TicketType reservedTicketType(UUID id, UUID eventId) {
        return TicketType.builder()
                .id(id)
                .eventId(eventId)
                .name("VIP")
                .kind(TicketTypeKind.RESERVED_SEATING)
                .price(Money.builder().amount(5000L).currency("USD").build())
                .quantityTotal(50)
                .quantityAvailable(50)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10)
                .build();
    }

    private Seat seat(UUID id, UUID seatMapId, SeatStatus status) {
        return Seat.builder().id(id).seatMapId(seatMapId).section("A").row("1").seatNumber("1").status(status).build();
    }

    private SeatMap seatMap(UUID id, UUID eventId) {
        return SeatMap.builder().id(id).eventId(eventId).build();
    }

    private CartItem cartItem(UUID id, UUID cartId, UUID ticketTypeId, UUID seatId, int quantity, Instant holdExpiresAt) {
        return CartItem.builder()
                .id(id)
                .cartId(cartId)
                .ticketTypeId(ticketTypeId)
                .seatId(seatId)
                .quantity(quantity)
                .holdExpiresAt(holdExpiresAt)
                .createdAt(Instant.now())
                .build();
    }

    private PromoCode promoCode(UUID id, UUID eventId, DiscountType type, BigDecimal value, Set<UUID> applicable, Instant validFrom, Instant validUntil) {
        return PromoCode.builder()
                .id(id)
                .eventId(eventId)
                .code("SAVE10")
                .discountType(type)
                .discountValue(value)
                .applicableTicketTypeIds(applicable)
                .validFrom(validFrom)
                .validUntil(validUntil)
                .build();
    }

    private void mockNoExpiredHolds() {
        when(cartItemRepository.findByTicketTypeIdAndHoldExpiresAtBefore(any(), any())).thenReturn(List.of());
    }

    // ---- create() ----

    @Test
    void create_savesCartOwnedByCaller() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse result = service.create();

        assertThat(result.buyerId()).isEqualTo(buyerId);
        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getBuyerId()).isEqualTo(buyerId);
    }

    // ---- get() ----

    @Test
    void get_unknownCart_throwsResourceNotFound() {
        UUID cartId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(cartId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_nonOwner_throwsForbidden() {
        UUID cartId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(strangerId);

        assertThatThrownBy(() -> service.get(cartId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void get_owner_succeedsAndReleasesExpiredHoldForGaTicketType() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Cart cartEntity = cart(cartId, buyerId);
        CartItem expiredItem = cartItem(itemId, cartId, ticketTypeId, null, 2, Instant.now().minus(1, ChronoUnit.MINUTES));
        TicketType ticketType = gaTicketType(ticketTypeId, UUID.randomUUID(), 3);

        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cartEntity));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        // First call (releaseExpiredHoldsForCart), second call (toResponse) - same list is fine to reuse for release,
        // then empty for toResponse since the item is deleted.
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(expiredItem)).thenReturn(List.of());
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(ticketType));

        CartResponse result = service.get(cartId);

        assertThat(result.id()).isEqualTo(cartId);
        ArgumentCaptor<TicketType> captor = ArgumentCaptor.forClass(TicketType.class);
        verify(ticketTypeRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantityAvailable()).isEqualTo(5); // 3 + 2 released
        verify(cartItemRepository).delete(expiredItem);
    }

    // ---- addItem() : authorization / cart & ticket type resolution ----

    @Test
    void addItem_unknownCart_throwsResourceNotFound() {
        UUID cartId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(UUID.randomUUID(), null, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addItem_nonOwner_throwsForbidden() {
        UUID cartId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(strangerId);

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(UUID.randomUUID(), null, 1)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void addItem_unknownTicketType_throwsResourceNotFound() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, null, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addItem_ticketTypeEventVanished_throwsResourceNotFound() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(gaTicketType(ticketTypeId, eventId, 10)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, null, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- addItem() : event-status / sale-window purchasability gating ----

    /**
     * Stubs only through the event-status resolution step. Tests that expect
     * addItem() to reach the single-event-per-cart check (and beyond) must
     * separately stub {@code cartItemRepository.findByCartId} themselves —
     * kept out of this shared helper so status/sale-window-rejection tests
     * (which throw before that point) don't trip Mockito's strict-stubs
     * unnecessary-stubbing check.
     */
    private void mockCartAndTicketType(UUID cartId, UUID ticketTypeId, TicketType ticketType, EventStatus eventStatus) {
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType));
        when(eventRepository.findByIdAndDeletedAtIsNull(ticketType.getEventId())).thenReturn(Optional.of(event(ticketType.getEventId(), eventStatus)));
    }

    @Test
    void addItem_draftEvent_throwsConflict() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType tt = gaTicketType(ticketTypeId, UUID.randomUUID(), 10);
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.DRAFT);

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, null, 1)))
                .isInstanceOf(ConflictException.class);
        verify(ticketTypeRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void addItem_cancelledEvent_throwsConflict() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType tt = gaTicketType(ticketTypeId, UUID.randomUUID(), 10);
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.CANCELLED);

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, null, 1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void addItem_completedEvent_throwsConflict() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType tt = gaTicketType(ticketTypeId, UUID.randomUUID(), 10);
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.COMPLETED);

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, null, 1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void addItem_beforeSaleWindow_throwsConflict() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType tt = gaTicketType(ticketTypeId, UUID.randomUUID(), 10);
        tt.setSaleStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
        tt.setSaleEndAt(Instant.now().plus(5, ChronoUnit.DAYS));
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, null, 1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void addItem_afterSaleWindow_throwsConflict() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType tt = gaTicketType(ticketTypeId, UUID.randomUUID(), 10);
        tt.setSaleStartAt(Instant.now().minus(5, ChronoUnit.DAYS));
        tt.setSaleEndAt(Instant.now().minus(1, ChronoUnit.DAYS));
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, null, 1)))
                .isInstanceOf(ConflictException.class);
    }

    // ---- addItem() : cart constrained to a single event ----

    @Test
    void addItem_differentEventThanExistingCartItems_throwsBadRequest() {
        UUID cartId = UUID.randomUUID();
        UUID existingTicketTypeId = UUID.randomUUID();
        UUID existingEventId = UUID.randomUUID();
        UUID newTicketTypeId = UUID.randomUUID();
        UUID newEventId = UUID.randomUUID();

        TicketType newTicketType = gaTicketType(newTicketTypeId, newEventId, 10);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(newTicketTypeId)).thenReturn(Optional.of(newTicketType));
        when(eventRepository.findByIdAndDeletedAtIsNull(newEventId)).thenReturn(Optional.of(event(newEventId, EventStatus.ON_SALE)));
        CartItem existingItem = cartItem(UUID.randomUUID(), cartId, existingTicketTypeId, null, 1, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(existingItem));
        when(ticketTypeRepository.findById(existingTicketTypeId)).thenReturn(Optional.of(gaTicketType(existingTicketTypeId, existingEventId, 5)));

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(newTicketTypeId, null, 1)))
                .isInstanceOf(BadRequestException.class);
        verify(ticketTypeRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void addItem_sameEventAsExistingCartItems_succeeds() {
        UUID cartId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID existingTicketTypeId = UUID.randomUUID();
        UUID newTicketTypeId = UUID.randomUUID();

        TicketType newTicketType = gaTicketType(newTicketTypeId, eventId, 10);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(newTicketTypeId)).thenReturn(Optional.of(newTicketType));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, EventStatus.ON_SALE)));
        CartItem existingItem = cartItem(UUID.randomUUID(), cartId, existingTicketTypeId, null, 1, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(existingItem));
        when(ticketTypeRepository.findById(existingTicketTypeId)).thenReturn(Optional.of(gaTicketType(existingTicketTypeId, eventId, 5)));
        when(ticketTypeRepository.findByIdForUpdate(newTicketTypeId)).thenReturn(Optional.of(newTicketType));
        mockNoExpiredHolds();

        service.addItem(cartId, new CartItemCreateRequest(newTicketTypeId, null, 1));

        verify(ticketTypeRepository).save(any(TicketType.class));
    }

    // ---- addItem() : GA path ----

    @Test
    void addItem_ga_insufficientQuantity_throwsConflict() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType tt = gaTicketType(ticketTypeId, UUID.randomUUID(), 1);
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(tt));
        mockNoExpiredHolds();

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, null, 2)))
                .isInstanceOf(ConflictException.class);
        verify(ticketTypeRepository, never()).save(any());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_ga_sufficientQuantity_decrementsAndCreatesHold() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType tt = gaTicketType(ticketTypeId, UUID.randomUUID(), 5);
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(tt));
        mockNoExpiredHolds();

        Instant before = Instant.now();
        service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, null, 2));
        Instant after = Instant.now();

        ArgumentCaptor<TicketType> ttCaptor = ArgumentCaptor.forClass(TicketType.class);
        verify(ticketTypeRepository).save(ttCaptor.capture());
        assertThat(ttCaptor.getValue().getQuantityAvailable()).isEqualTo(3);

        ArgumentCaptor<CartItem> itemCaptor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(itemCaptor.capture());
        CartItem saved = itemCaptor.getValue();
        assertThat(saved.getTicketTypeId()).isEqualTo(ticketTypeId);
        assertThat(saved.getSeatId()).isNull();
        assertThat(saved.getQuantity()).isEqualTo(2);
        // Hold expiry ~15 minutes out (BR-INV-003).
        assertThat(saved.getHoldExpiresAt()).isBetween(
                before.plus(14, ChronoUnit.MINUTES), after.plus(16, ChronoUnit.MINUTES));
    }

    @Test
    void addItem_ga_quantityOmitted_defaultsToOne() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType tt = gaTicketType(ticketTypeId, UUID.randomUUID(), 5);
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(tt));
        mockNoExpiredHolds();

        service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, null, null));

        ArgumentCaptor<CartItem> itemCaptor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getQuantity()).isEqualTo(1);
    }

    @Test
    void addItem_ga_releasesExpiredHoldsForSameTicketTypeBeforeChecking() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType tt = gaTicketType(ticketTypeId, UUID.randomUUID(), 0);
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(tt));
        CartItem expired = cartItem(UUID.randomUUID(), UUID.randomUUID(), ticketTypeId, null, 3, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(cartItemRepository.findByTicketTypeIdAndHoldExpiresAtBefore(any(), any())).thenReturn(List.of(expired));

        service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, null, 2));

        verify(cartItemRepository).delete(expired);
        ArgumentCaptor<TicketType> ttCaptor = ArgumentCaptor.forClass(TicketType.class);
        verify(ticketTypeRepository).save(ttCaptor.capture());
        // 0 (initial) + 3 (released) - 2 (this request) = 1
        assertThat(ttCaptor.getValue().getQuantityAvailable()).isEqualTo(1);
    }

    // ---- addItem() : reserved-seating path ----

    @Test
    void addItem_reservedSeating_missingSeatId_throwsBadRequest() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType tt = reservedTicketType(ticketTypeId, UUID.randomUUID());
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, null, 1)))
                .isInstanceOf(BadRequestException.class);
        verify(seatRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void addItem_reservedSeating_unknownSeat_throwsResourceNotFound() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        TicketType tt = reservedTicketType(ticketTypeId, UUID.randomUUID());
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());
        when(seatRepository.findByIdForUpdate(seatId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, seatId, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addItem_reservedSeating_seatMapVanished_throwsResourceNotFound() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID seatMapId = UUID.randomUUID();
        TicketType tt = reservedTicketType(ticketTypeId, UUID.randomUUID());
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());
        when(seatRepository.findByIdForUpdate(seatId)).thenReturn(Optional.of(seat(seatId, seatMapId, SeatStatus.AVAILABLE)));
        when(seatMapRepository.findById(seatMapId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, seatId, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addItem_reservedSeating_seatBelongsToDifferentEvent_throwsBadRequest() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID seatMapId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        TicketType tt = reservedTicketType(ticketTypeId, eventId);
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());
        when(seatRepository.findByIdForUpdate(seatId)).thenReturn(Optional.of(seat(seatId, seatMapId, SeatStatus.AVAILABLE)));
        when(seatMapRepository.findById(seatMapId)).thenReturn(Optional.of(seatMap(seatMapId, UUID.randomUUID()))); // different event

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, seatId, null)))
                .isInstanceOf(BadRequestException.class);
        verify(seatRepository, never()).save(any());
    }

    @Test
    void addItem_reservedSeating_seatNotAvailable_noStaleHold_throwsConflict() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID seatMapId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        TicketType tt = reservedTicketType(ticketTypeId, eventId);
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());
        when(seatRepository.findByIdForUpdate(seatId)).thenReturn(Optional.of(seat(seatId, seatMapId, SeatStatus.HELD)));
        when(seatMapRepository.findById(seatMapId)).thenReturn(Optional.of(seatMap(seatMapId, eventId)));
        mockNoExpiredHolds();

        assertThatThrownBy(() -> service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, seatId, null)))
                .isInstanceOf(ConflictException.class);
        verify(seatRepository, never()).save(any());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_reservedSeating_available_succeeds() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID seatMapId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        TicketType tt = reservedTicketType(ticketTypeId, eventId);
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());
        Seat availableSeat = seat(seatId, seatMapId, SeatStatus.AVAILABLE);
        when(seatRepository.findByIdForUpdate(seatId)).thenReturn(Optional.of(availableSeat));
        when(seatMapRepository.findById(seatMapId)).thenReturn(Optional.of(seatMap(seatMapId, eventId)));
        mockNoExpiredHolds();

        service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, seatId, null));

        ArgumentCaptor<Seat> seatCaptor = ArgumentCaptor.forClass(Seat.class);
        verify(seatRepository).save(seatCaptor.capture());
        assertThat(seatCaptor.getValue().getStatus()).isEqualTo(SeatStatus.HELD);

        ArgumentCaptor<CartItem> itemCaptor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getSeatId()).isEqualTo(seatId);
        assertThat(itemCaptor.getValue().getQuantity()).isEqualTo(1);
    }

    @Test
    void addItem_reservedSeating_staleHoldOnThisSpecificSeat_releasedThenSucceeds() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID seatMapId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        TicketType tt = reservedTicketType(ticketTypeId, eventId);
        mockCartAndTicketType(cartId, ticketTypeId, tt, EventStatus.ON_SALE);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());
        // Seat is still marked HELD in the DB, but its hold has expired.
        Seat staleSeat = seat(seatId, seatMapId, SeatStatus.HELD);
        when(seatRepository.findByIdForUpdate(seatId)).thenReturn(Optional.of(staleSeat));
        when(seatMapRepository.findById(seatMapId)).thenReturn(Optional.of(seatMap(seatMapId, eventId)));
        CartItem staleItem = cartItem(UUID.randomUUID(), UUID.randomUUID(), ticketTypeId, seatId, 1, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(cartItemRepository.findByTicketTypeIdAndHoldExpiresAtBefore(any(), any())).thenReturn(List.of(staleItem));

        service.addItem(cartId, new CartItemCreateRequest(ticketTypeId, seatId, null));

        verify(cartItemRepository).delete(staleItem);
        ArgumentCaptor<Seat> seatCaptor = ArgumentCaptor.forClass(Seat.class);
        verify(seatRepository).save(seatCaptor.capture());
        assertThat(seatCaptor.getValue().getStatus()).isEqualTo(SeatStatus.HELD); // re-held by the new request
        verify(cartItemRepository).save(any(CartItem.class));
    }

    // ---- removeItem() ----

    @Test
    void removeItem_unknownCart_throwsResourceNotFound() {
        UUID cartId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeItem(cartId, UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeItem_nonOwner_throwsForbidden() {
        UUID cartId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(strangerId);

        assertThatThrownBy(() -> service.removeItem(cartId, UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removeItem_itemBelongsToDifferentCart_throwsResourceNotFound() {
        UUID cartId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        CartItem itemInOtherCart = cartItem(itemId, UUID.randomUUID(), UUID.randomUUID(), null, 1, Instant.now());
        when(cartItemRepository.findById(itemId)).thenReturn(Optional.of(itemInOtherCart));

        assertThatThrownBy(() -> service.removeItem(cartId, itemId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeItem_gaItem_restoresQuantityAndDeletesRow() {
        UUID cartId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        CartItem item = cartItem(itemId, cartId, ticketTypeId, null, 2, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(cartItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        TicketType tt = gaTicketType(ticketTypeId, UUID.randomUUID(), 3);
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(tt));

        service.removeItem(cartId, itemId);

        ArgumentCaptor<TicketType> ttCaptor = ArgumentCaptor.forClass(TicketType.class);
        verify(ticketTypeRepository).save(ttCaptor.capture());
        assertThat(ttCaptor.getValue().getQuantityAvailable()).isEqualTo(5);
        verify(cartItemRepository).delete(item);
    }

    @Test
    void removeItem_seatItem_releasesSeatBackToAvailable() {
        UUID cartId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        CartItem item = cartItem(itemId, cartId, ticketTypeId, seatId, 1, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(cartItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        Seat heldSeat = seat(seatId, UUID.randomUUID(), SeatStatus.HELD);
        when(seatRepository.findByIdForUpdate(seatId)).thenReturn(Optional.of(heldSeat));

        service.removeItem(cartId, itemId);

        ArgumentCaptor<Seat> seatCaptor = ArgumentCaptor.forClass(Seat.class);
        verify(seatRepository).save(seatCaptor.capture());
        assertThat(seatCaptor.getValue().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        verify(cartItemRepository).delete(item);
    }

    // ---- applyPromoCode() ----

    @Test
    void applyPromoCode_unknownCart_throwsResourceNotFound() {
        UUID cartId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyPromoCode(cartId, "SAVE10")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void applyPromoCode_nonOwner_throwsForbidden() {
        UUID cartId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(strangerId);

        assertThatThrownBy(() -> service.applyPromoCode(cartId, "SAVE10")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void applyPromoCode_emptyCart_throwsUnprocessableEntity() {
        UUID cartId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.applyPromoCode(cartId, "SAVE10")).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void applyPromoCode_cartSpansMultipleEvents_throwsUnprocessableEntity() {
        UUID cartId = UUID.randomUUID();
        UUID ticketType1 = UUID.randomUUID();
        UUID ticketType2 = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(
                cartItem(UUID.randomUUID(), cartId, ticketType1, null, 1, Instant.now().plus(10, ChronoUnit.MINUTES)),
                cartItem(UUID.randomUUID(), cartId, ticketType2, null, 1, Instant.now().plus(10, ChronoUnit.MINUTES))));
        when(ticketTypeRepository.findById(ticketType1)).thenReturn(Optional.of(gaTicketType(ticketType1, UUID.randomUUID(), 5)));
        when(ticketTypeRepository.findById(ticketType2)).thenReturn(Optional.of(gaTicketType(ticketType2, UUID.randomUUID(), 5)));

        assertThatThrownBy(() -> service.applyPromoCode(cartId, "SAVE10")).isInstanceOf(UnprocessableEntityException.class);
        verify(promoCodeRepository, never()).findByEventIdAndCodeAndDeletedAtIsNull(any(), any());
    }

    private UUID setUpSingleItemCart(UUID cartId, UUID ticketTypeId, UUID eventId, long priceAmount) {
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(
                cartItem(UUID.randomUUID(), cartId, ticketTypeId, null, 1, Instant.now().plus(10, ChronoUnit.MINUTES))));
        TicketType tt = gaTicketType(ticketTypeId, eventId, 5);
        tt.setPrice(Money.builder().amount(priceAmount).currency("USD").build());
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(tt));
        return eventId;
    }

    @Test
    void applyPromoCode_unknownCode_throwsUnprocessableEntity() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        setUpSingleItemCart(cartId, ticketTypeId, eventId, 1000L);
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyPromoCode(cartId, "SAVE10")).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void applyPromoCode_expiredCode_throwsUnprocessableEntity() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        setUpSingleItemCart(cartId, ticketTypeId, eventId, 1000L);
        PromoCode expired = promoCode(UUID.randomUUID(), eventId, DiscountType.FIXED, BigDecimal.valueOf(100),
                Set.of(), Instant.now().minus(10, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.applyPromoCode(cartId, "SAVE10")).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void applyPromoCode_notYetValidCode_throwsUnprocessableEntity() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        setUpSingleItemCart(cartId, ticketTypeId, eventId, 1000L);
        PromoCode notYetValid = promoCode(UUID.randomUUID(), eventId, DiscountType.FIXED, BigDecimal.valueOf(100),
                Set.of(), Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(10, ChronoUnit.DAYS));
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.of(notYetValid));

        assertThatThrownBy(() -> service.applyPromoCode(cartId, "SAVE10")).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void applyPromoCode_inapplicableTicketType_throwsUnprocessableEntity() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        setUpSingleItemCart(cartId, ticketTypeId, eventId, 1000L);
        PromoCode restricted = promoCode(UUID.randomUUID(), eventId, DiscountType.FIXED, BigDecimal.valueOf(100),
                Set.of(UUID.randomUUID()), // some OTHER ticket type, not the one in the cart
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.of(restricted));

        assertThatThrownBy(() -> service.applyPromoCode(cartId, "SAVE10")).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void applyPromoCode_usageLimitGuardRejects_propagatesUnprocessableEntity() {
        // PromoCodeUsageLimitGuard's own BR-PROMO-002/006 logic is unit-tested
        // separately (PromoCodeUsageLimitGuardTest); this only proves
        // applyPromoCode() actually consults it and propagates its rejection,
        // now that the check has been factored out of this class.
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        setUpSingleItemCart(cartId, ticketTypeId, eventId, 1000L);
        PromoCode promo = promoCode(UUID.randomUUID(), eventId, DiscountType.FIXED, BigDecimal.valueOf(100),
                Set.of(), Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.of(promo));
        org.mockito.Mockito.doThrow(new UnprocessableEntityException("Promo code has reached its total usage limit"))
                .when(promoCodeUsageLimitGuard).checkUsageLimits(promo, buyerId);

        assertThatThrownBy(() -> service.applyPromoCode(cartId, "SAVE10")).isInstanceOf(UnprocessableEntityException.class);
        verify(cartRepository, never()).saveAndFlush(any(Cart.class));
    }

    @Test
    void applyPromoCode_emptyApplicableSet_appliesToAllTicketTypes_percentageDiscount() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        setUpSingleItemCart(cartId, ticketTypeId, eventId, 1000L); // subtotal = 1000
        PromoCode promo = promoCode(UUID.randomUUID(), eventId, DiscountType.PERCENTAGE, BigDecimal.valueOf(10),
                Set.of(), Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.of(promo));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(promoCodeRepository.findById(promo.getId())).thenReturn(Optional.of(promo));

        CartResponse result = service.applyPromoCode(cartId, "SAVE10");

        assertThat(result.appliedPromoCode()).isNotNull();
        assertThat(result.appliedPromoCode().discountAmount().amount()).isEqualTo(100L); // 10% of 1000
        assertThat(result.total().amount()).isEqualTo(900L);
        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).saveAndFlush(cartCaptor.capture());
        assertThat(cartCaptor.getValue().getPromoCodeId()).isEqualTo(promo.getId());
    }

    @Test
    void applyPromoCode_fixedDiscount_appliesFlatAmount() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        setUpSingleItemCart(cartId, ticketTypeId, eventId, 1000L); // subtotal = 1000
        PromoCode promo = promoCode(UUID.randomUUID(), eventId, DiscountType.FIXED, BigDecimal.valueOf(300),
                Set.of(ticketTypeId), Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.of(promo));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(promoCodeRepository.findById(promo.getId())).thenReturn(Optional.of(promo));

        CartResponse result = service.applyPromoCode(cartId, "SAVE10");

        assertThat(result.appliedPromoCode().discountAmount().amount()).isEqualTo(300L);
        assertThat(result.total().amount()).isEqualTo(700L);
    }

    @Test
    void applyPromoCode_discountExceedsSubtotal_clampedToZero_neverNegative() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        setUpSingleItemCart(cartId, ticketTypeId, eventId, 500L); // subtotal = 500
        PromoCode promo = promoCode(UUID.randomUUID(), eventId, DiscountType.FIXED, BigDecimal.valueOf(999999),
                Set.of(), Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
        when(promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, "SAVE10")).thenReturn(Optional.of(promo));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(promoCodeRepository.findById(promo.getId())).thenReturn(Optional.of(promo));

        CartResponse result = service.applyPromoCode(cartId, "SAVE10");

        assertThat(result.total().amount()).isEqualTo(0L);
        assertThat(result.appliedPromoCode().discountAmount().amount()).isEqualTo(500L); // clamped to subtotal, not 999999
    }

    // ---- removePromoCode() ----

    @Test
    void removePromoCode_unknownCart_throwsResourceNotFound() {
        UUID cartId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removePromoCode(cartId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removePromoCode_nonOwner_throwsForbidden() {
        UUID cartId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(accessGuard.currentUserId()).thenReturn(strangerId);

        assertThatThrownBy(() -> service.removePromoCode(cartId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removePromoCode_clearsPromoCodeAndRevertsTotal() {
        UUID cartId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID promoCodeId = UUID.randomUUID();
        Cart cartWithPromo = cart(cartId, buyerId);
        cartWithPromo.setPromoCodeId(promoCodeId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cartWithPromo));
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(
                cartItem(UUID.randomUUID(), cartId, ticketTypeId, null, 1, Instant.now().plus(10, ChronoUnit.MINUTES))));
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(gaTicketType(ticketTypeId, UUID.randomUUID(), 5)));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse result = service.removePromoCode(cartId);

        assertThat(result.appliedPromoCode()).isNull();
        assertThat(result.total().amount()).isEqualTo(1000L); // back to subtotal, no discount
        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).saveAndFlush(cartCaptor.capture());
        assertThat(cartCaptor.getValue().getPromoCodeId()).isNull();
        verify(promoCodeRepository, times(0)).findById(promoCodeId);
    }
}
