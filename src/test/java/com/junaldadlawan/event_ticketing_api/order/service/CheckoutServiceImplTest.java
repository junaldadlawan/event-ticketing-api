package com.junaldadlawan.event_ticketing_api.order.service;

import com.junaldadlawan.event_ticketing_api.cart.dto.AppliedPromoCodeResponse;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartResponse;
import com.junaldadlawan.event_ticketing_api.cart.entity.Cart;
import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.cart.service.CartService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.GoneException;
import com.junaldadlawan.event_ticketing_api.common.exception.PaymentFailedException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.exception.UnprocessableEntityException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.entity.CheckoutIdempotencyKey;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.entity.Payment;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.enums.PaymentStatus;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentResult;
import com.junaldadlawan.event_ticketing_api.order.repository.CheckoutIdempotencyKeyRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PaymentRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.promocode.service.PromoCodeUsageLimitGuard;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketCredentialService;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link CheckoutServiceImpl} (no Spring context, no
 * real DB) — mirrors {@code CartServiceImplTest}'s style. Covers the
 * branching logic that's meaningfully unit-testable with mocked
 * repositories/collaborators: authorization, idempotency-key claim/replay/
 * conflict/cross-buyer branching, empty-cart/expired-hold/payment-failure
 * short-circuits (and that each frees the idempotency key), the
 * promo-usage-limit re-check before charging, and the successful-checkout
 * Order/Payment/inventory-finalization side effects for both GA and
 * reserved-seating items.
 * <p>
 * The real pessimistic-locking semantics of {@code CartRepository.findByIdForUpdate}
 * (code-reviewer CRITICAL 1) and the real {@code Persistable}/{@code persist()}
 * vs {@code merge()} behavior of {@code CheckoutIdempotencyKey} (CRITICAL 2)
 * cannot be meaningfully proven against mocked repositories — those stay
 * covered by real-DB concurrent requests in {@code CheckoutIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private CartService cartService;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CheckoutIdempotencyKeyRepository idempotencyKeyRepository;
    @Mock
    private CheckoutIdempotencyKeyManager idempotencyKeyManager;
    @Mock
    private PaymentGatewayClient paymentGatewayClient;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PromoCodeUsageLimitGuard promoCodeUsageLimitGuard;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketCredentialService ticketCredentialService;

    private CheckoutServiceImpl service;

    private UUID buyerId;
    private UUID cartId;
    private UUID idempotencyKey;

    @BeforeEach
    void setUp() {
        service = new CheckoutServiceImpl(
                cartRepository, cartItemRepository, cartService, ticketTypeRepository, seatRepository,
                eventRepository, orderRepository, paymentRepository, idempotencyKeyRepository,
                idempotencyKeyManager, paymentGatewayClient, accessGuard, promoCodeRepository,
                notificationService, promoCodeUsageLimitGuard, ticketRepository, ticketCredentialService);
        buyerId = UUID.randomUUID();
        cartId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID();
    }

    // ---- fixtures ----

    private Cart cart(UUID id, UUID buyerId) {
        return Cart.builder().id(id).buyerId(buyerId).createdAt(Instant.now()).build();
    }

    private CartItem gaItem(UUID cartId, UUID ticketTypeId, int quantity, Instant holdExpiresAt) {
        return CartItem.builder()
                .id(UUID.randomUUID())
                .cartId(cartId)
                .ticketTypeId(ticketTypeId)
                .seatId(null)
                .quantity(quantity)
                .holdExpiresAt(holdExpiresAt)
                .build();
    }

    private CartItem seatItem(UUID cartId, UUID ticketTypeId, UUID seatId, Instant holdExpiresAt) {
        return CartItem.builder()
                .id(UUID.randomUUID())
                .cartId(cartId)
                .ticketTypeId(ticketTypeId)
                .seatId(seatId)
                .quantity(1)
                .holdExpiresAt(holdExpiresAt)
                .build();
    }

    private TicketType ticketType(UUID id, UUID eventId) {
        return TicketType.builder()
                .id(id)
                .eventId(eventId)
                .name("GA")
                .kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .build();
    }

    private Event event(UUID id, UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return Event.builder()
                .id(id)
                .organizationId(organizationId)
                .title("Concert")
                .ticketPrefix("ABC")
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .build();
    }

    /** Stubs ticket issuance's collaborators for any test that reaches a successful checkout. */
    private void stubTicketIssuance() {
        when(ticketRepository.existsByEventIdAndTicketNumber(any(), any())).thenReturn(false);
        when(ticketCredentialService.generate(any())).thenReturn("stub-credential");
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CartResponse cartView(UUID cartId, UUID buyerId, long total, String promoCode) {
        AppliedPromoCodeResponse applied = promoCode != null
                ? new AppliedPromoCodeResponse(promoCode, new MoneyDto(0L, "USD"))
                : null;
        return new CartResponse(cartId, buyerId, List.of(), applied, new MoneyDto(total, "USD"), Instant.now(), Instant.now());
    }

    private void stubFreshClaim() {
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId))
                .thenReturn(new CheckoutIdempotencyKeyManager.ClaimOutcome(null, true));
    }

    // ---- pre-idempotency-claim guards: unknown cart / non-owner ----

    @Test
    void checkout_unknownCart_throwsResourceNotFound() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkout(cartId, idempotencyKey, "tok_ok"))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(idempotencyKeyManager);
    }

    @Test
    void checkout_nonOwner_throwsForbidden() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, UUID.randomUUID())));

        assertThatThrownBy(() -> service.checkout(cartId, idempotencyKey, "tok_ok"))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(idempotencyKeyManager);
    }

    // ---- idempotency-key claim branching ----

    @Test
    void checkout_crossBuyerIdempotencyKey_throwsForbidden_andDoesNotFreeKey() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        CheckoutIdempotencyKey existing = CheckoutIdempotencyKey.builder()
                .id(idempotencyKey).buyerId(UUID.randomUUID()).cartId(UUID.randomUUID()).build();
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId))
                .thenReturn(new CheckoutIdempotencyKeyManager.ClaimOutcome(existing, false));

        assertThatThrownBy(() -> service.checkout(cartId, idempotencyKey, "tok_ok"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("different buyer");

        // Outside the try/catch that frees a key on failure - this path never claimed anything of ours.
        verify(idempotencyKeyManager, never()).delete(any());
    }

    @Test
    void checkout_replayWithSameKey_returnsSameOrder_noRecharge() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        UUID existingOrderId = UUID.randomUUID();
        CheckoutIdempotencyKey existing = CheckoutIdempotencyKey.builder()
                .id(idempotencyKey).buyerId(buyerId).cartId(cartId).orderId(existingOrderId).build();
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId))
                .thenReturn(new CheckoutIdempotencyKeyManager.ClaimOutcome(existing, false));
        Order existingOrder = Order.builder()
                .id(existingOrderId).buyerId(buyerId).payeeType(PayeeType.ORGANIZATION).payeeId(UUID.randomUUID())
                .status(OrderStatus.PAID).cartId(cartId).total(Money.builder().amount(1000L).currency("USD").build())
                .createdBy(buyerId.toString()).createdAt(Instant.now()).build();
        when(orderRepository.findById(existingOrderId)).thenReturn(Optional.of(existingOrder));
        when(ticketRepository.findByOrderId(existingOrderId)).thenReturn(List.of());

        OrderResponse response = service.checkout(cartId, idempotencyKey, "tok_ok");

        assertThat(response.id()).isEqualTo(existingOrderId);
        assertThat(response.tickets()).isEmpty();
        verifyNoInteractions(paymentGatewayClient);
        verify(orderRepository, never()).saveAndFlush(any());
        verify(idempotencyKeyManager, never()).delete(any());
    }

    @Test
    void checkout_inFlightDuplicateKey_throwsConflict() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        // orderId still null: a genuinely concurrent request already claimed this key, not yet completed.
        CheckoutIdempotencyKey existing = CheckoutIdempotencyKey.builder()
                .id(idempotencyKey).buyerId(buyerId).cartId(cartId).orderId(null).build();
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId))
                .thenReturn(new CheckoutIdempotencyKeyManager.ClaimOutcome(existing, false));

        assertThatThrownBy(() -> service.checkout(cartId, idempotencyKey, "tok_ok"))
                .isInstanceOf(ConflictException.class);

        verify(idempotencyKeyManager, never()).delete(any());
    }

    // ---- fresh claim: empty cart / expired hold / promo re-check / payment failure, each frees the key ----

    @Test
    void checkout_emptyCart_throwsConflict_andFreesKey() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        stubFreshClaim();
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.checkout(cartId, idempotencyKey, "tok_ok"))
                .isInstanceOf(ConflictException.class);

        verify(idempotencyKeyManager).delete(idempotencyKey);
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    void checkout_expiredHold_throwsGone_releasesHolds_andFreesKey() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        stubFreshClaim();
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        UUID ticketTypeId = UUID.randomUUID();
        CartItem expiredItem = gaItem(cartId, ticketTypeId, 1, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(expiredItem));

        assertThatThrownBy(() -> service.checkout(cartId, idempotencyKey, "tok_ok"))
                .isInstanceOf(GoneException.class);

        verify(cartService).releaseExpiredHoldsForCart(cartId);
        verify(idempotencyKeyManager).delete(idempotencyKey);
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    void checkout_promoUsageLimitExhaustedSinceApply_throwsUnprocessableEntity_beforeCharging_andFreesKey() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        stubFreshClaim();
        UUID promoCodeId = UUID.randomUUID();
        Cart lockedCart = cart(cartId, buyerId);
        lockedCart.setPromoCodeId(promoCodeId);
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(lockedCart));
        UUID ticketTypeId = UUID.randomUUID();
        CartItem item = gaItem(cartId, ticketTypeId, 1, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(cartService.get(cartId)).thenReturn(cartView(cartId, buyerId, 900L, "SAVE10"));
        PromoCode promoCode = PromoCode.builder().id(promoCodeId).code("SAVE10")
                .discountType(DiscountType.FIXED).discountValue(BigDecimal.valueOf(100)).build();
        when(promoCodeRepository.findById(promoCodeId)).thenReturn(Optional.of(promoCode));
        org.mockito.Mockito.doThrow(new UnprocessableEntityException("Promo code has reached its total usage limit"))
                .when(promoCodeUsageLimitGuard).checkUsageLimits(promoCode, buyerId);

        assertThatThrownBy(() -> service.checkout(cartId, idempotencyKey, "tok_ok"))
                .isInstanceOf(UnprocessableEntityException.class);

        verifyNoInteractions(paymentGatewayClient);
        verify(orderRepository, never()).saveAndFlush(any());
        verify(idempotencyKeyManager).delete(idempotencyKey);
    }

    @Test
    void checkout_paymentDeclined_throwsPaymentFailed_noOrderOrPaymentPersisted_andFreesKey() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        stubFreshClaim();
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        CartItem item = gaItem(cartId, ticketTypeId, 1, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(cartService.get(cartId)).thenReturn(cartView(cartId, buyerId, 1000L, null));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(paymentGatewayClient.charge(org.mockito.ArgumentMatchers.eq("tok_fail"), any()))
                .thenReturn(PaymentResult.failure("Payment declined by gateway for the provided payment method token"));

        assertThatThrownBy(() -> service.checkout(cartId, idempotencyKey, "tok_fail"))
                .isInstanceOf(PaymentFailedException.class);

        verify(orderRepository, never()).saveAndFlush(any());
        verify(paymentRepository, never()).save(any());
        verify(cartItemRepository, never()).delete(any());
        verify(idempotencyKeyManager).delete(idempotencyKey);
    }

    // ---- successful checkout ----

    @Test
    void checkout_success_ga_createsOrderAndPayment_deletesCartItem_clearsPromoCode_doesNotFreeKey() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        stubFreshClaim();
        UUID promoCodeId = UUID.randomUUID();
        Cart lockedCart = cart(cartId, buyerId);
        lockedCart.setPromoCodeId(promoCodeId); // must be cleared on success
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(lockedCart));
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        CartItem item = gaItem(cartId, ticketTypeId, 2, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(cartService.get(cartId)).thenReturn(cartView(cartId, buyerId, 1800L, "SAVE10"));
        PromoCode promoCode = PromoCode.builder().id(promoCodeId).code("SAVE10")
                .discountType(DiscountType.FIXED).discountValue(BigDecimal.valueOf(200)).build();
        when(promoCodeRepository.findById(promoCodeId)).thenReturn(Optional.of(promoCode));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, organizationId)));
        when(paymentGatewayClient.charge(org.mockito.ArgumentMatchers.eq("tok_ok"), any()))
                .thenReturn(PaymentResult.success("mock_ref_123"));
        stubTicketIssuance();
        UUID savedOrderId = UUID.randomUUID();
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.saveAndFlush(orderCaptor.capture())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(savedOrderId);
            o.setCreatedAt(Instant.now());
            return o;
        });
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        when(paymentRepository.save(paymentCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        CheckoutIdempotencyKey keyRow = CheckoutIdempotencyKey.builder().id(idempotencyKey).buyerId(buyerId).cartId(cartId).build();
        when(idempotencyKeyRepository.findById(idempotencyKey)).thenReturn(Optional.of(keyRow));

        OrderResponse response = service.checkout(cartId, idempotencyKey, "tok_ok");

        assertThat(response.id()).isEqualTo(savedOrderId);
        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(response.payeeType()).isEqualTo(PayeeType.ORGANIZATION);
        assertThat(response.payeeId()).isEqualTo(organizationId);
        assertThat(response.total().amount()).isEqualTo(1800L);
        // quantity=2 GA CartItem issues 2 separate Ticket rows (confirmed decision #2).
        assertThat(response.tickets()).hasSize(2);
        assertThat(response.tickets()).allSatisfy(ticket -> {
            assertThat(ticket.orderId()).isEqualTo(savedOrderId);
            assertThat(ticket.eventId()).isEqualTo(eventId);
            assertThat(ticket.ticketTypeId()).isEqualTo(ticketTypeId);
            assertThat(ticket.seatId()).isNull();
            assertThat(ticket.ownerId()).isEqualTo(buyerId);
            assertThat(ticket.ticketNumber()).startsWith("ABC-");
            assertThat(ticket.status()).isEqualTo(TicketStatus.VALID);
        });

        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getBuyerId()).isEqualTo(buyerId);
        assertThat(savedOrder.getCartId()).isEqualTo(cartId);
        assertThat(savedOrder.getPromoCode()).isEqualTo("SAVE10");

        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getOrderId()).isEqualTo(savedOrderId);
        assertThat(savedPayment.getGatewayRef()).isEqualTo("mock_ref_123");
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        verify(cartItemRepository).delete(item);
        assertThat(lockedCart.getPromoCodeId()).isNull();
        verify(cartRepository).save(lockedCart);
        assertThat(keyRow.getOrderId()).isEqualTo(savedOrderId);
        verify(idempotencyKeyRepository).save(keyRow);
        verify(idempotencyKeyManager, never()).delete(any());
        verify(ticketRepository, org.mockito.Mockito.times(2)).save(any(Ticket.class));
    }

    @Test
    void checkout_success_reservedSeating_flipsSeatFromHeldToSold() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        stubFreshClaim();
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        CartItem item = seatItem(cartId, ticketTypeId, seatId, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(cartService.get(cartId)).thenReturn(cartView(cartId, buyerId, 5000L, null));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(paymentGatewayClient.charge(org.mockito.ArgumentMatchers.eq("tok_ok"), any()))
                .thenReturn(PaymentResult.success("mock_ref_456"));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            o.setCreatedAt(Instant.now());
            return o;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Seat heldSeat = Seat.builder().id(seatId).status(SeatStatus.HELD).build();
        when(seatRepository.findByIdForUpdate(seatId)).thenReturn(Optional.of(heldSeat));
        when(idempotencyKeyRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        stubTicketIssuance();

        OrderResponse response = service.checkout(cartId, idempotencyKey, "tok_ok");

        assertThat(heldSeat.getStatus()).isEqualTo(SeatStatus.SOLD);
        verify(seatRepository).save(heldSeat);
        verify(cartItemRepository).delete(item);
        // Reserved-seating CartItem (quantity always 1) issues exactly 1 Ticket, tied to its seat.
        assertThat(response.tickets()).hasSize(1);
        assertThat(response.tickets().get(0).seatId()).isEqualTo(seatId);
    }

    @Test
    void checkout_success_noAppliedPromoCode_promoUsageGuardNeverInvoked() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        stubFreshClaim();
        // lockedCart has no promoCodeId at all - the checkout-time re-check must not run.
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        CartItem item = gaItem(cartId, ticketTypeId, 1, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(cartService.get(cartId)).thenReturn(cartView(cartId, buyerId, 1000L, null));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(paymentGatewayClient.charge(org.mockito.ArgumentMatchers.eq("tok_ok"), any()))
                .thenReturn(PaymentResult.success("mock_ref_789"));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            o.setCreatedAt(Instant.now());
            return o;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyKeyRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        stubTicketIssuance();

        service.checkout(cartId, idempotencyKey, "tok_ok");

        verifyNoInteractions(promoCodeUsageLimitGuard);
        verify(promoCodeRepository, never()).findById(any());
    }

    // ---- BR-NOTIFY-001 (Phase 11): checkout fires ORDER_CONFIRMATION + PAYMENT_RECEIPT ----

    @Test
    void checkout_success_firesOrderConfirmationAndPaymentReceiptNotifications_toTheBuyer() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        stubFreshClaim();
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        CartItem item = gaItem(cartId, ticketTypeId, 1, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(cartService.get(cartId)).thenReturn(cartView(cartId, buyerId, 1000L, null));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(paymentGatewayClient.charge(org.mockito.ArgumentMatchers.eq("tok_ok"), any()))
                .thenReturn(PaymentResult.success("mock_ref_notify"));
        UUID savedOrderId = UUID.randomUUID();
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(savedOrderId);
            o.setCreatedAt(Instant.now());
            return o;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyKeyRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        stubTicketIssuance();

        service.checkout(cartId, idempotencyKey, "tok_ok");

        verify(notificationService).notify(buyerId, NotificationType.ORDER_CONFIRMATION, "Order", savedOrderId);
        verify(notificationService).notify(buyerId, NotificationType.PAYMENT_RECEIPT, "Order", savedOrderId);
        verify(notificationService, org.mockito.Mockito.times(2)).notify(any(), any(), any(), any());
    }

    @Test
    void checkout_replayWithSameKey_doesNotFireAnyNotifications() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        UUID existingOrderId = UUID.randomUUID();
        CheckoutIdempotencyKey existing = CheckoutIdempotencyKey.builder()
                .id(idempotencyKey).buyerId(buyerId).cartId(cartId).orderId(existingOrderId).build();
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId))
                .thenReturn(new CheckoutIdempotencyKeyManager.ClaimOutcome(existing, false));
        Order existingOrder = Order.builder()
                .id(existingOrderId).buyerId(buyerId).payeeType(PayeeType.ORGANIZATION).payeeId(UUID.randomUUID())
                .status(OrderStatus.PAID).cartId(cartId).total(Money.builder().amount(1000L).currency("USD").build())
                .createdBy(buyerId.toString()).createdAt(Instant.now()).build();
        when(orderRepository.findById(existingOrderId)).thenReturn(Optional.of(existingOrder));
        when(ticketRepository.findByOrderId(existingOrderId)).thenReturn(List.of());

        service.checkout(cartId, idempotencyKey, "tok_ok");

        // Replay of an already-completed checkout isn't a new purchase - no
        // additional ORDER_CONFIRMATION/PAYMENT_RECEIPT should fire.
        verifyNoInteractions(notificationService);
    }

    @Test
    void checkout_paymentDeclined_doesNotFireAnyNotifications() {
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        stubFreshClaim();
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart(cartId, buyerId)));
        UUID ticketTypeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        CartItem item = gaItem(cartId, ticketTypeId, 1, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(cartService.get(cartId)).thenReturn(cartView(cartId, buyerId, 1000L, null));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(paymentGatewayClient.charge(org.mockito.ArgumentMatchers.eq("tok_fail"), any()))
                .thenReturn(PaymentResult.failure("Payment declined by gateway for the provided payment method token"));

        assertThatThrownBy(() -> service.checkout(cartId, idempotencyKey, "tok_fail"))
                .isInstanceOf(PaymentFailedException.class);

        verifyNoInteractions(notificationService);
    }
}
