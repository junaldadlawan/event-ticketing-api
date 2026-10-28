package com.junaldadlawan.event_ticketing_api.order.service;

import com.junaldadlawan.event_ticketing_api.cart.dto.CartResponse;
import com.junaldadlawan.event_ticketing_api.cart.entity.Cart;
import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.cart.service.CartService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import com.junaldadlawan.event_ticketing_api.order.dto.CheckoutStartResponse;
import com.junaldadlawan.event_ticketing_api.order.entity.CheckoutIdempotencyKey;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.entity.Payment;
import com.junaldadlawan.event_ticketing_api.order.entity.PendingCheckout;
import com.junaldadlawan.event_ticketing_api.order.enums.PendingCheckoutStatus;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentResult;
import com.junaldadlawan.event_ticketing_api.order.repository.CheckoutIdempotencyKeyRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PaymentRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PendingCheckoutRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.platformfee.service.PlatformFeeQuote;
import com.junaldadlawan.event_ticketing_api.platformfee.service.PlatformFeeService;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.promocode.service.PromoCodeUsageLimitGuard;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
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
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checkouts that wait for the payment provider's webhook (PayMongo): starting one, and what the paid / failed webhooks do. The
 * order must exist only once the payment succeeded; a paid checkout that can no longer be issued is refunded.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplPendingTest {

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
    @Mock
    private PlatformFeeService platformFeeService;
    @Mock
    private com.junaldadlawan.event_ticketing_api.gatewayfee.service.GatewayFeeService gatewayFeeService;
    @Mock
    private PendingCheckoutRepository pendingCheckoutRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    private CheckoutServiceImpl service;

    private UUID buyerId;
    private UUID cartId;
    private UUID idempotencyKey;
    private UUID ticketTypeId;

    @BeforeEach
    void setUp() {
        service = new CheckoutServiceImpl(
                cartRepository, cartItemRepository, cartService, ticketTypeRepository, seatRepository,
                eventRepository, org.mockito.Mockito.mock(com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository.class), orderRepository, paymentRepository, idempotencyKeyRepository,
                idempotencyKeyManager, paymentGatewayClient, accessGuard, promoCodeRepository,
                notificationService, promoCodeUsageLimitGuard, ticketRepository, ticketCredentialService, platformFeeService,
                pendingCheckoutRepository, transactionManager, gatewayFeeService, null, null, null,
                org.mockito.Mockito.mock(com.junaldadlawan.event_ticketing_api.tickettype.service.TicketTypeBuyerLimit.class), org.mockito.Mockito.mock(com.junaldadlawan.event_ticketing_api.analytics.service.CartActivityRecorder.class));
        buyerId = UUID.randomUUID();
        cartId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
        lenient().when(accessGuard.currentUserId()).thenReturn(buyerId);
        lenient().when(platformFeeService.quote(any(), any(), anyLong(), any(), anyInt())).thenReturn(PlatformFeeQuote.NONE);
        lenient().when(gatewayFeeService.quote(anyLong(), any())).thenReturn(com.junaldadlawan.event_ticketing_api.gatewayfee.service.GatewayFeeQuote.NONE);
    }

    // ---- fixtures ----

    private Cart cart() {
        return Cart.builder().id(cartId).buyerId(buyerId).createdAt(Instant.now()).build();
    }

    /** A cart with one ticket worth 1000 centavos (PHP) held for another ten minutes. */
    private void stubPriceable(Instant holdExpiresAt) {
        UUID eventId = UUID.randomUUID();
        lenient().when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart()));
        lenient().when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart()));
        lenient().when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(CartItem.builder().id(UUID.randomUUID()).cartId(cartId)
                .ticketTypeId(ticketTypeId).quantity(1).holdExpiresAt(holdExpiresAt).build()));
        CartResponse view = new CartResponse(cartId, buyerId, List.of(), null,
                new MoneyDto(0L, "PHP"), new MoneyDto(0L, "PHP"), new MoneyDto(1000L, "PHP"), Instant.now(), Instant.now());
        lenient().when(cartService.get(cartId)).thenReturn(view);
        lenient().when(cartService.getForBuyer(cartId, buyerId)).thenReturn(view);   // the webhook has no logged-in user
        lenient().when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(TicketType.builder()
                .id(ticketTypeId).eventId(eventId).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("PHP").build()).build()));
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        lenient().when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(Event.builder().id(eventId)
                .organizationId(UUID.randomUUID()).title("Concert").ticketPrefix("ABC").startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).build()));
    }

    private void stubFreshClaim() {
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId, null))
                .thenReturn(new CheckoutIdempotencyKeyManager.ClaimOutcome(null, true));
    }

    private PendingCheckout pending(PendingCheckoutStatus status, long amount) {
        return PendingCheckout.builder().id(UUID.randomUUID()).cartId(cartId).buyerId(buyerId).idempotencyKey(idempotencyKey)
                .providerIntentId("pi_1").clientKey("pi_1_client_x").amount(amount).currency("PHP").status(status).build();
    }

    private void stubIssuing() {
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            o.setCreatedAt(Instant.now());
            return o;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.existsByEventIdAndTicketNumber(any(), any())).thenReturn(false);
        when(ticketCredentialService.generate(any())).thenReturn("credential");
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyKeyRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
    }

    // ---- starting a checkout ----

    @Test
    void isAsynchronous_followsTheGateway() {
        when(paymentGatewayClient.confirmsByWebhook()).thenReturn(true);

        assertThat(service.isAsynchronous()).isTrue();
    }

    @Test
    void startCheckout_createsTheIntentAndAPendingCheckout_butNoOrder() {
        stubPriceable(Instant.now().plus(10, ChronoUnit.MINUTES));
        stubFreshClaim();
        when(paymentGatewayClient.createPaymentIntent(any(), anyString(), anyString()))
                .thenReturn(new PaymentGatewayClient.PaymentIntentInfo("pi_1", "pi_1_client_x"));
        when(pendingCheckoutRepository.save(any(PendingCheckout.class))).thenAnswer(inv -> {
            PendingCheckout p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        CheckoutStartResponse response = service.startCheckout(cartId, idempotencyKey);

        assertThat(response.status()).isEqualTo(PendingCheckoutStatus.PENDING);
        assertThat(response.clientKey()).isEqualTo("pi_1_client_x");
        assertThat(response.amount()).isEqualTo(1000L);
        assertThat(response.currency()).isEqualTo("PHP");
        assertThat(response.orderId()).isNull();
        ArgumentCaptor<Money> charged = ArgumentCaptor.forClass(Money.class);
        verify(paymentGatewayClient).createPaymentIntent(charged.capture(), anyString(), eq("checkout-" + idempotencyKey));
        assertThat(charged.getValue().getAmount()).isEqualTo(1000L);
        verify(orderRepository, never()).saveAndFlush(any());   // BR-CART-002: no order until the payment succeeded
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void startCheckout_repeatedWithTheSameKey_returnsTheSameCheckout_withoutAnotherIntent() {
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart()));
        CheckoutIdempotencyKey existingKey = CheckoutIdempotencyKey.builder().id(idempotencyKey).buyerId(buyerId).cartId(cartId).build();
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId, null))
                .thenReturn(new CheckoutIdempotencyKeyManager.ClaimOutcome(existingKey, false));
        PendingCheckout existing = pending(PendingCheckoutStatus.PENDING, 1000L);
        when(pendingCheckoutRepository.findFirstByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey)).thenReturn(Optional.of(existing));

        CheckoutStartResponse response = service.startCheckout(cartId, idempotencyKey);

        assertThat(response.checkoutId()).isEqualTo(existing.getId());
        verify(paymentGatewayClient, never()).createPaymentIntent(any(), any(), any());
    }

    @Test
    void startCheckout_aKeyUsedByAnotherBuyer_isForbidden() {
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart()));
        CheckoutIdempotencyKey someoneElses = CheckoutIdempotencyKey.builder().id(idempotencyKey).buyerId(UUID.randomUUID()).cartId(cartId).build();
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId, null))
                .thenReturn(new CheckoutIdempotencyKeyManager.ClaimOutcome(someoneElses, false));

        assertThatThrownBy(() -> service.startCheckout(cartId, idempotencyKey)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void startCheckout_aKeyInUseWithNoCheckoutYet_isAConflict() {
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart()));
        CheckoutIdempotencyKey inFlight = CheckoutIdempotencyKey.builder().id(idempotencyKey).buyerId(buyerId).cartId(cartId).build();
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId, null))
                .thenReturn(new CheckoutIdempotencyKeyManager.ClaimOutcome(inFlight, false));
        when(pendingCheckoutRepository.findFirstByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startCheckout(cartId, idempotencyKey)).isInstanceOf(ConflictException.class);
    }

    @Test
    void startCheckout_whenTheProviderRefuses_freesTheKey_andCreatesNothing() {
        stubPriceable(Instant.now().plus(10, ChronoUnit.MINUTES));
        stubFreshClaim();
        when(paymentGatewayClient.createPaymentIntent(any(), anyString(), anyString())).thenThrow(new IllegalStateException("provider down"));

        assertThatThrownBy(() -> service.startCheckout(cartId, idempotencyKey)).isInstanceOf(IllegalStateException.class);

        verify(idempotencyKeyManager).delete(idempotencyKey);
        verify(pendingCheckoutRepository, never()).save(any());
    }

    @Test
    void startCheckout_someoneElsesCart_isForbidden() {
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(Cart.builder().id(cartId).buyerId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.startCheckout(cartId, idempotencyKey)).isInstanceOf(ForbiddenException.class);
        verify(paymentGatewayClient, never()).createPaymentIntent(any(), any(), any());
    }

    // ---- webhook: paid ----

    @Test
    void onPaymentPaid_issuesTheOrderAndTickets_andMarksTheCheckoutPaid() {
        PendingCheckout pending = pending(PendingCheckoutStatus.PENDING, 1000L);
        when(pendingCheckoutRepository.findByProviderIntentIdForUpdate("pi_1")).thenReturn(Optional.of(pending));
        stubPriceable(Instant.now().plus(10, ChronoUnit.MINUTES));
        stubIssuing();

        service.onPaymentPaid("pi_1", "pay_1", 1000L, "PHP");

        assertThat(pending.getStatus()).isEqualTo(PendingCheckoutStatus.PAID);
        assertThat(pending.getOrderId()).isNotNull();
        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(payment.getValue().getGatewayRef()).isEqualTo("pay_1");   // the payment id a refund needs
        assertThat(payment.getValue().getAmount().getAmount()).isEqualTo(1000L);
        verify(paymentGatewayClient, never()).refund(any(), any());
    }

    @Test
    void onPaymentPaid_withAForeignCard_recordsTheForeignFeeThatApplied_andADomesticCardTheDomesticOne() {
        var feeQuote = new com.junaldadlawan.event_ticketing_api.gatewayfee.service.GatewayFeeQuote(50, com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer.ORGANIZATION,
                new java.math.BigDecimal("5"), 0L, 80, new java.math.BigDecimal("8"), 0L);
        when(gatewayFeeService.quote(anyLong(), any())).thenReturn(feeQuote);
        when(pendingCheckoutRepository.findByProviderIntentIdForUpdate("pi_1")).thenReturn(
                Optional.of(pending(PendingCheckoutStatus.PENDING, 1000L)), Optional.of(pending(PendingCheckoutStatus.PENDING, 1000L)),
                Optional.of(pending(PendingCheckoutStatus.PENDING, 1000L)));
        stubPriceable(Instant.now().plus(10, ChronoUnit.MINUTES));
        stubIssuing();

        for (String country : new String[] {"US", "PH", null}) {
            service.onPaymentPaid("pi_1", "pay_1", 1000L, "PHP", country);
        }

        ArgumentCaptor<Order> orders = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, org.mockito.Mockito.atLeast(3)).saveAndFlush(orders.capture());
        var saved = orders.getAllValues();
        assertThat(saved.get(0).getGatewayFeeCardOrigin()).isEqualTo(com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeeCardOrigin.FOREIGN);
        assertThat(saved.get(0).getGatewayFeeAppliedAmount()).isEqualTo(80L);
        assertThat(saved.get(0).getGatewayFeeAmount()).isEqualTo(50L);
        assertThat(saved.get(0).getGatewayFeeForeignPercentage()).isEqualByComparingTo("8");
        assertThat(saved.get(1).getGatewayFeeCardOrigin()).isEqualTo(com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeeCardOrigin.DOMESTIC);
        assertThat(saved.get(1).getGatewayFeeAppliedAmount()).isEqualTo(50L);
        assertThat(saved.get(2).getGatewayFeeCardOrigin()).isNull();   // the gateway did not say: the domestic fee, as before
        assertThat(saved.get(2).getGatewayFeeAppliedAmount()).isEqualTo(50L);
    }

    @Test
    void onPaymentPaid_aWebhookDeliveredTwice_issuesTheOrderOnce() {
        PendingCheckout alreadyPaid = pending(PendingCheckoutStatus.PAID, 1000L);
        when(pendingCheckoutRepository.findByProviderIntentIdForUpdate("pi_1")).thenReturn(Optional.of(alreadyPaid));

        service.onPaymentPaid("pi_1", "pay_1", 1000L, "PHP");

        verify(orderRepository, never()).saveAndFlush(any());
        verify(paymentGatewayClient, never()).refund(any(), any());
    }

    @Test
    void onPaymentPaid_anUnknownIntent_isIgnored() {
        when(pendingCheckoutRepository.findByProviderIntentIdForUpdate("pi_unknown")).thenReturn(Optional.empty());

        service.onPaymentPaid("pi_unknown", "pay_1", 1000L, "PHP");

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void onPaymentPaid_whenTheAmountPaidIsNotTheOrderTotal_refundsInsteadOfIssuing() {
        PendingCheckout pending = pending(PendingCheckoutStatus.PENDING, 1000L);
        when(pendingCheckoutRepository.findByProviderIntentIdForUpdate("pi_1")).thenReturn(Optional.of(pending));
        stubPriceable(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(paymentGatewayClient.refund(eq("pay_1"), any())).thenReturn(PaymentResult.success("ref_1"));

        service.onPaymentPaid("pi_1", "pay_1", 900L, "PHP");

        assertThat(pending.getStatus()).isEqualTo(PendingCheckoutStatus.REFUNDED);
        assertThat(pending.getFailureReason()).contains("does not match");
        verify(orderRepository, never()).saveAndFlush(any());
        verify(idempotencyKeyManager).delete(idempotencyKey);
    }

    @Test
    void onPaymentPaid_whenTheHoldExpiredWhilePaying_refundsThePayment_andIssuesNothing() {
        PendingCheckout pending = pending(PendingCheckoutStatus.PENDING, 1000L);
        when(pendingCheckoutRepository.findByProviderIntentIdForUpdate("pi_1")).thenReturn(Optional.of(pending));
        stubPriceable(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(paymentGatewayClient.refund(eq("pay_1"), any())).thenReturn(PaymentResult.success("ref_1"));

        service.onPaymentPaid("pi_1", "pay_1", 1000L, "PHP");

        assertThat(pending.getStatus()).isEqualTo(PendingCheckoutStatus.REFUNDED);
        verify(cartService).releaseExpiredHoldsForCart(cartId);
        verify(paymentGatewayClient).refund(eq("pay_1"), any(Money.class));
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void onPaymentPaid_aRefundThatFails_isRecordedForManualAttention() {
        PendingCheckout pending = pending(PendingCheckoutStatus.PENDING, 1000L);
        when(pendingCheckoutRepository.findByProviderIntentIdForUpdate("pi_1")).thenReturn(Optional.of(pending));
        stubPriceable(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(paymentGatewayClient.refund(eq("pay_1"), any())).thenReturn(PaymentResult.failure("provider said no"));

        service.onPaymentPaid("pi_1", "pay_1", 1000L, "PHP");

        assertThat(pending.getStatus()).isEqualTo(PendingCheckoutStatus.REFUNDED);
        assertThat(pending.getFailureReason()).contains("manual attention");
    }

    // ---- webhook: failed ----

    @Test
    void onPaymentFailed_marksTheCheckoutFailed_andFreesTheKeyForARetry() {
        PendingCheckout pending = pending(PendingCheckoutStatus.PENDING, 1000L);
        when(pendingCheckoutRepository.findByProviderIntentIdForUpdate("pi_1")).thenReturn(Optional.of(pending));

        service.onPaymentFailed("pi_1", "Your card was declined.");

        assertThat(pending.getStatus()).isEqualTo(PendingCheckoutStatus.FAILED);
        assertThat(pending.getFailureReason()).isEqualTo("Your card was declined.");
        verify(idempotencyKeyManager).delete(idempotencyKey);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void onPaymentFailed_forACheckoutAlreadyPaid_changesNothing() {
        PendingCheckout paid = pending(PendingCheckoutStatus.PAID, 1000L);
        when(pendingCheckoutRepository.findByProviderIntentIdForUpdate("pi_1")).thenReturn(Optional.of(paid));

        service.onPaymentFailed("pi_1", "late failure");

        assertThat(paid.getStatus()).isEqualTo(PendingCheckoutStatus.PAID);
        verify(idempotencyKeyManager, never()).delete(any());
    }

    // ---- status ----

    @Test
    void getStatus_isForTheBuyerOnly() {
        PendingCheckout mine = pending(PendingCheckoutStatus.PENDING, 1000L);
        when(pendingCheckoutRepository.findByIdAndBuyerId(mine.getId(), buyerId)).thenReturn(Optional.of(mine));
        UUID someoneElses = UUID.randomUUID();
        when(pendingCheckoutRepository.findByIdAndBuyerId(someoneElses, buyerId)).thenReturn(Optional.empty());

        assertThat(service.getStatus(mine.getId()).status()).isEqualTo(PendingCheckoutStatus.PENDING);
        assertThatThrownBy(() -> service.getStatus(someoneElses)).isInstanceOf(ResourceNotFoundException.class);
    }
}
