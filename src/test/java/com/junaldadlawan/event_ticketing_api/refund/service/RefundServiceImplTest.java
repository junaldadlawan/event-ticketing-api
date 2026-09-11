package com.junaldadlawan.event_ticketing_api.refund.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.entity.Payment;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.enums.PaymentStatus;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentResult;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PaymentRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.refund.dto.RefundCreateRequest;
import com.junaldadlawan.event_ticketing_api.refund.dto.RefundResponse;
import com.junaldadlawan.event_ticketing_api.refund.entity.Refund;
import com.junaldadlawan.event_ticketing_api.refund.enums.RefundStatus;
import com.junaldadlawan.event_ticketing_api.refund.repository.RefundRepository;
import com.junaldadlawan.event_ticketing_api.refundpolicy.entity.RefundPolicy;
import com.junaldadlawan.event_ticketing_api.refundpolicy.enums.RefundRuleType;
import com.junaldadlawan.event_ticketing_api.refundpolicy.repository.RefundPolicyRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.waitlist.service.WaitlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link RefundServiceImpl} (no Spring context).
 * Covers BR-PAY-002's organizer/admin-only gate on {@code createRefund}
 * (deliberately NOT buyer-initiated), BR-PAY-003's policy-gate branches, the
 * remaining-balance math (including a prior-partial-refund-then-another
 * scenario), the full-refund-marks-tickets-REFUNDED behavior, and the
 * gateway-failure-still-creates-a-FAILED-refund-row behavior for both the
 * organizer-initiated and event-cancellation entry points.
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock
    private RefundRepository refundRepository;
    @Mock
    private RefundPolicyRepository refundPolicyRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private PaymentGatewayClient paymentGatewayClient;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private WaitlistService waitlistService;
    @Mock
    private NotificationService notificationService;

    private RefundServiceImpl service;

    private UUID orgId;
    private UUID eventId;
    private UUID orderId;
    private UUID buyerId;

    @BeforeEach
    void setUp() {
        service = new RefundServiceImpl(refundRepository, refundPolicyRepository, orderRepository,
                paymentRepository, ticketRepository, eventRepository, paymentGatewayClient, accessGuard,
                ticketTypeRepository, waitlistService, notificationService);
        orgId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        buyerId = UUID.randomUUID();
    }

    // ---- fixtures ----

    private Event event(Instant startAt) {
        return Event.builder()
                .id(eventId)
                .organizationId(orgId)
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

    private Order order(long totalAmount, OrderStatus status) {
        return Order.builder()
                .id(orderId)
                .buyerId(buyerId)
                .payeeType(PayeeType.ORGANIZATION)
                .payeeId(orgId)
                .status(status)
                .total(Money.builder().amount(totalAmount).currency("USD").build())
                .createdBy(buyerId.toString())
                .build();
    }

    private Ticket ticket(UUID id, UUID belongsToOrderId) {
        return Ticket.builder()
                .id(id).orderId(belongsToOrderId).eventId(eventId).ticketTypeId(UUID.randomUUID())
                .ownerId(buyerId).ticketNumber("T-1").credential("cred").status(TicketStatus.VALID)
                .build();
    }

    private Payment payment(String gatewayRef) {
        return Payment.builder()
                .id(UUID.randomUUID()).orderId(orderId).gatewayRef(gatewayRef)
                .amount(Money.builder().amount(1000L).currency("USD").build())
                .status(PaymentStatus.COMPLETED)
                .build();
    }

    private void stubOrderAndEventLookup(Order order) {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        // lenient: only reached once a caller passes authorization (code-reviewer
        // CRITICAL fix added a second, locked re-fetch for the read-then-write
        // critical section) - the forbidden-path tests using this helper never
        // reach it, same idiom as EventServiceImplTest.mockOwnerAccess.
        org.mockito.Mockito.lenient().when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        Ticket anyTicket = ticket(UUID.randomUUID(), orderId);
        when(ticketRepository.findFirstByOrderId(orderId)).thenReturn(Optional.of(anyTicket));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(Instant.now().plus(30, ChronoUnit.DAYS))));
    }

    private void stubOrganizerCaller(UUID organizerId) {
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
    }

    private RefundCreateRequest requestWithReason(String reason) {
        return new RefundCreateRequest(null, reason);
    }

    // ---- createRefund(): resolution ----

    @Test
    void createRefund_unknownOrder_throwsResourceNotFound() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRefund(orderId, requestWithReason("wrong size")))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard, refundRepository);
    }

    // ---- createRefund(): BR-PAY-002 organizer/admin-only gate ----

    @Test
    void createRefund_buyerAttemptsSelfRefund_throwsForbidden_notOrganizerOrAdmin() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        // The order's own buyer is neither an org member nor an admin.
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(accessGuard.hasRole(buyerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(buyerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.createRefund(orderId, requestWithReason("buyer self-refund attempt")))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(refundRepository, paymentGatewayClient);
    }

    @Test
    void createRefund_roselessStranger_throwsForbidden() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        UUID strangerId = UUID.randomUUID();
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.createRefund(orderId, requestWithReason("not my order")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createRefund_admin_bypassesOrgRoleCheck() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        when(accessGuard.isAdmin()).thenReturn(true);
        when(accessGuard.currentUserId()).thenReturn(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 1000L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_1"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> {
            Refund r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of());

        RefundResponse response = service.createRefund(orderId, requestWithReason("admin-initiated"));

        assertThat(response.status()).isEqualTo(RefundStatus.COMPLETED);
        verify(accessGuard, never()).hasRole(any(), any(), any());
    }

    // ---- createRefund(): remaining-balance math ----

    @Test
    void createRefund_orderAlreadyFullyRefunded_throwsConflict() {
        Order order = order(1000L, OrderStatus.REFUNDED);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(1000L);

        assertThatThrownBy(() -> service.createRefund(orderId, requestWithReason("already refunded")))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(paymentGatewayClient);
        // Currency/policy checks never reached once the balance is exhausted.
        verify(refundPolicyRepository, never()).findByEventId(any());
    }

    @Test
    void createRefund_explicitAmountCurrencyMismatch_throwsBadRequest() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L);

        RefundCreateRequest request = new RefundCreateRequest(new MoneyDto(500L, "EUR"), "wrong currency");

        assertThatThrownBy(() -> service.createRefund(orderId, request))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(paymentGatewayClient);
        verify(refundPolicyRepository, never()).findByEventId(any());
    }

    /** Code-reviewer MEDIUM: a $0 "refund" must not create a meaningless COMPLETED Refund row or flip Order.status. */
    @Test
    void createRefund_zeroExplicitAmount_throwsBadRequest() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L);

        RefundCreateRequest request = new RefundCreateRequest(new MoneyDto(0L, "USD"), "zero amount");

        assertThatThrownBy(() -> service.createRefund(orderId, request))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(paymentGatewayClient);
        verify(refundRepository, never()).save(any());
    }

    @Test
    void createRefund_explicitAmountExceedsRemainingBalance_throwsBadRequest() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(300L); // remaining = 700

        RefundCreateRequest request = new RefundCreateRequest(new MoneyDto(701L, "USD"), "too much");

        assertThatThrownBy(() -> service.createRefund(orderId, request))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    void createRefund_explicitAmountExactlyAtRemainingBalance_succeeds() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(300L, 1000L); // remaining = 700, exactly requested
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_2"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of());

        RefundCreateRequest request = new RefundCreateRequest(new MoneyDto(700L, "USD"), "exact remaining");
        RefundResponse response = service.createRefund(orderId, request);

        assertThat(response.amount().amount()).isEqualTo(700L);
    }

    // ---- createRefund(): BR-PAY-003 policy gate ----

    @Test
    void createRefund_noRefundPolicyRowAtAll_throwsConflict() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L);
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRefund(orderId, requestWithReason("no policy configured")))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    void createRefund_noRefundsPolicy_throwsConflict() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.NO_REFUNDS).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> service.createRefund(orderId, requestWithReason("no refunds allowed")))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    void createRefund_refundableUntilNDays_withinWindow_succeeds() {
        Order order = order(1000L, OrderStatus.PAID);
        // Event starts 10 days out; policy allows refunds up to 5 days before -> still within window.
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        Ticket anyTicket = ticket(UUID.randomUUID(), orderId);
        when(ticketRepository.findFirstByOrderId(orderId)).thenReturn(Optional.of(anyTicket));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(Instant.now().plus(10, ChronoUnit.DAYS))));
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 1000L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.REFUNDABLE_UNTIL_N_DAYS).daysBeforeEvent(5).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_3"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of());

        RefundResponse response = service.createRefund(orderId, requestWithReason("within window"));

        assertThat(response.status()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    void createRefund_refundableUntilNDays_pastWindow_throwsConflict() {
        Order order = order(1000L, OrderStatus.PAID);
        // Event starts in 3 days; policy only allows refunds up to 5 days before -> past the window now.
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        Ticket anyTicket = ticket(UUID.randomUUID(), orderId);
        when(ticketRepository.findFirstByOrderId(orderId)).thenReturn(Optional.of(anyTicket));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(Instant.now().plus(3, ChronoUnit.DAYS))));
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.REFUNDABLE_UNTIL_N_DAYS).daysBeforeEvent(5).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> service.createRefund(orderId, requestWithReason("past the window")))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    void createRefund_customPolicy_alwaysAllowed() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 400L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).customTerms("case-by-case").build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_4"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundCreateRequest request = new RefundCreateRequest(new MoneyDto(400L, "USD"), "custom case-by-case approval");
        RefundResponse response = service.createRefund(orderId, request);

        assertThat(response.status()).isEqualTo(RefundStatus.COMPLETED);
    }

    // ---- createRefund(): omitted amount -> full remaining balance ----

    @Test
    void createRefund_omittedAmount_refundsFullRemainingBalance() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 1000L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        ArgumentCaptor<Money> amountCaptor = ArgumentCaptor.forClass(Money.class);
        when(paymentGatewayClient.refund(eq("mock_ref"), amountCaptor.capture())).thenReturn(PaymentResult.success("mock_refund_5"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of());

        RefundResponse response = service.createRefund(orderId, requestWithReason("full refund, no amount specified"));

        assertThat(response.amount().amount()).isEqualTo(1000L);
        assertThat(amountCaptor.getValue().getAmount()).isEqualTo(1000L);
    }

    // ---- issueRefund mechanics: full refund marks tickets REFUNDED ----

    @Test
    void createRefund_fullRefund_transitionsOrderToRefunded_andMarksAllTicketsRefunded() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 1000L); // cumulative reaches full total
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_6"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        Ticket t1 = ticket(UUID.randomUUID(), orderId);
        Ticket t2 = ticket(UUID.randomUUID(), orderId);
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of(t1, t2));
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(orderCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.createRefund(orderId, requestWithReason("full refund"));

        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(t1.getStatus()).isEqualTo(TicketStatus.REFUNDED);
        assertThat(t2.getStatus()).isEqualTo(TicketStatus.REFUNDED);
        verify(ticketRepository, times(2)).save(any(Ticket.class));
    }

    /**
     * Code-reviewer CRITICAL: {@code Ticket.orderId} never changes on a
     * Phase 7 transfer/resale - only {@code ownerId} does. Refunding the
     * ORIGINAL order must not strip a ticket from whoever legitimately
     * holds it now if that's no longer the order's own buyer - they never
     * sold it back and were paid nothing.
     */
    @Test
    void createRefund_fullRefund_ticketAlreadyTransferredToSomeoneElse_leavesThatTicketAlone() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 1000L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_transferred"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        // Still owned by the order's original buyer - must be refunded.
        Ticket stillOwnedByBuyer = ticket(UUID.randomUUID(), orderId);
        // Transferred away to someone else since purchase - must be left alone.
        Ticket transferredAway = ticket(UUID.randomUUID(), orderId);
        transferredAway.setOwnerId(UUID.randomUUID());
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of(stillOwnedByBuyer, transferredAway));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createRefund(orderId, requestWithReason("full refund after a partial transfer"));

        assertThat(stillOwnedByBuyer.getStatus()).isEqualTo(TicketStatus.REFUNDED);
        assertThat(transferredAway.getStatus()).isEqualTo(TicketStatus.VALID);
        verify(ticketRepository, times(1)).save(any(Ticket.class));
    }

    @Test
    void createRefund_partialRefund_transitionsOrderToPartiallyRefunded_leavesTicketsUntouched() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 400L); // does not reach full total
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_7"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(orderCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        RefundCreateRequest request = new RefundCreateRequest(new MoneyDto(400L, "USD"), "partial refund");
        service.createRefund(orderId, request);

        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PARTIALLY_REFUNDED);
        // A partial refund is a monetary adjustment only - tickets are never touched.
        verify(ticketRepository, never()).findByOrderId(any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void createRefund_priorPartialRefund_thenAnotherRefundReachingFullTotal_marksTicketsRefunded() {
        // Order total 1000, a prior COMPLETED refund of 300 already exists.
        // This new refund (omitted amount -> remaining = 700) pushes the
        // cumulative total to exactly 1000 -> full refund behavior fires.
        Order order = order(1000L, OrderStatus.PARTIALLY_REFUNDED);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(300L, 1000L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        ArgumentCaptor<Money> amountCaptor = ArgumentCaptor.forClass(Money.class);
        when(paymentGatewayClient.refund(eq("mock_ref"), amountCaptor.capture())).thenReturn(PaymentResult.success("mock_refund_8"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        Ticket t1 = ticket(UUID.randomUUID(), orderId);
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of(t1));
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(orderCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        RefundResponse response = service.createRefund(orderId, requestWithReason("second, final refund"));

        assertThat(response.amount().amount()).isEqualTo(700L); // only the remaining balance, not the full order total
        assertThat(amountCaptor.getValue().getAmount()).isEqualTo(700L);
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(t1.getStatus()).isEqualTo(TicketStatus.REFUNDED);
    }

    // ---- issueRefund mechanics: gateway failure still creates a FAILED refund row ----

    @Test
    void createRefund_gatewayFailure_stillCreatesFailedRefundRow_doesNotTouchOrderOrTickets() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L); // called only once - failure path skips the post-save recompute
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("gwref_fail_123")));
        when(paymentGatewayClient.refund(eq("gwref_fail_123"), any())).thenReturn(PaymentResult.failure("declined"));
        ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
        when(refundRepository.save(refundCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        RefundResponse response = service.createRefund(orderId, requestWithReason("gateway will decline this"));

        assertThat(response.status()).isEqualTo(RefundStatus.FAILED);
        assertThat(refundCaptor.getValue().getStatus()).isEqualTo(RefundStatus.FAILED);
        verify(orderRepository, never()).save(any());
        verify(ticketRepository, never()).findByOrderId(any());
        verify(ticketRepository, never()).save(any());
        verify(refundRepository, times(1)).sumCompletedAmountByOrderId(orderId);
    }

    @Test
    void createRefund_missingPaymentRow_throwsResourceNotFound() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createRefund(orderId, requestWithReason("no payment on file")))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(paymentGatewayClient);
    }

    // ---- listRefunds(): visibility ----

    @Test
    void listRefunds_unknownOrder_throwsResourceNotFound() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listRefunds(orderId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listRefunds_owningBuyer_isPermitted() {
        Order order = order(1000L, OrderStatus.PAID);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        Refund r = Refund.builder().id(UUID.randomUUID()).orderId(orderId)
                .amount(Money.builder().amount(500L).currency("USD").build())
                .reason("r").initiatedBy(UUID.randomUUID()).status(RefundStatus.COMPLETED).build();
        when(refundRepository.findByOrderId(orderId)).thenReturn(List.of(r));

        List<RefundResponse> result = service.listRefunds(orderId);

        assertThat(result).hasSize(1);
        verifyNoInteractions(ticketRepository, eventRepository);
    }

    @Test
    void listRefunds_admin_isPermitted() {
        Order order = order(1000L, OrderStatus.PAID);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(refundRepository.findByOrderId(orderId)).thenReturn(List.of());

        List<RefundResponse> result = service.listRefunds(orderId);

        assertThat(result).isEmpty();
        verify(accessGuard, never()).currentUserId();
    }

    @Test
    void listRefunds_eventOrganizer_isPermitted() {
        Order order = order(1000L, OrderStatus.PAID);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accessGuard.isAdmin()).thenReturn(false);
        UUID organizerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        Ticket anyTicket = ticket(UUID.randomUUID(), orderId);
        when(ticketRepository.findFirstByOrderId(orderId)).thenReturn(Optional.of(anyTicket));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(Instant.now().plus(10, ChronoUnit.DAYS))));
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        when(refundRepository.findByOrderId(orderId)).thenReturn(List.of());

        List<RefundResponse> result = service.listRefunds(orderId);

        assertThat(result).isEmpty();
    }

    @Test
    void listRefunds_roselessStrangerWithNoOrgTie_throwsForbidden() {
        Order order = order(1000L, OrderStatus.PAID);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(accessGuard.isAdmin()).thenReturn(false);
        UUID strangerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        Ticket anyTicket = ticket(UUID.randomUUID(), orderId);
        when(ticketRepository.findFirstByOrderId(orderId)).thenReturn(Optional.of(anyTicket));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(Instant.now().plus(10, ChronoUnit.DAYS))));
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.listRefunds(orderId)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(refundRepository);
    }

    // ---- refundAllForEventCancellation(): BR-PAY-005 ----

    @Test
    void refundAllForEventCancellation_bypassesRefundPolicyEntirely() {
        UUID initiatedBy = UUID.randomUUID();
        when(ticketRepository.findDistinctOrderIdsByEventId(eventId)).thenReturn(List.of(orderId));
        Order order = order(1000L, OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 1000L);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_cancel"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.refundAllForEventCancellation(eventId, initiatedBy);

        verifyNoInteractions(refundPolicyRepository);
        verify(refundRepository).save(any(Refund.class));
    }

    @Test
    void refundAllForEventCancellation_skipsOrdersAlreadyRefundedOrCancelled() {
        UUID refundedOrderId = UUID.randomUUID();
        UUID cancelledOrderId = UUID.randomUUID();
        when(ticketRepository.findDistinctOrderIdsByEventId(eventId)).thenReturn(List.of(refundedOrderId, cancelledOrderId));
        when(orderRepository.findByIdForUpdate(refundedOrderId)).thenReturn(Optional.of(
                Order.builder().id(refundedOrderId).buyerId(buyerId).payeeType(PayeeType.ORGANIZATION).payeeId(orgId)
                        .status(OrderStatus.REFUNDED).total(Money.builder().amount(500L).currency("USD").build())
                        .createdBy(buyerId.toString()).build()));
        when(orderRepository.findByIdForUpdate(cancelledOrderId)).thenReturn(Optional.of(
                Order.builder().id(cancelledOrderId).buyerId(buyerId).payeeType(PayeeType.ORGANIZATION).payeeId(orgId)
                        .status(OrderStatus.CANCELLED).total(Money.builder().amount(500L).currency("USD").build())
                        .createdBy(buyerId.toString()).build()));

        service.refundAllForEventCancellation(eventId, UUID.randomUUID());

        verifyNoInteractions(paymentGatewayClient, refundRepository, paymentRepository);
    }

    @Test
    void refundAllForEventCancellation_oneOrdersFailureDoesNotStopTheRest_bestEffort() {
        UUID missingPaymentOrderId = UUID.randomUUID();
        UUID healthyOrderId = UUID.randomUUID();
        when(ticketRepository.findDistinctOrderIdsByEventId(eventId))
                .thenReturn(List.of(missingPaymentOrderId, healthyOrderId));

        Order brokenOrder = Order.builder().id(missingPaymentOrderId).buyerId(buyerId).payeeType(PayeeType.ORGANIZATION)
                .payeeId(orgId).status(OrderStatus.PAID).total(Money.builder().amount(500L).currency("USD").build())
                .createdBy(buyerId.toString()).build();
        when(orderRepository.findByIdForUpdate(missingPaymentOrderId)).thenReturn(Optional.of(brokenOrder));
        when(refundRepository.sumCompletedAmountByOrderId(missingPaymentOrderId)).thenReturn(0L);
        when(paymentRepository.findByOrderId(missingPaymentOrderId)).thenReturn(List.of()); // no Payment row -> issueRefund throws

        Order healthyOrder = Order.builder().id(healthyOrderId).buyerId(buyerId).payeeType(PayeeType.ORGANIZATION)
                .payeeId(orgId).status(OrderStatus.PAID).total(Money.builder().amount(800L).currency("USD").build())
                .createdBy(buyerId.toString()).build();
        when(orderRepository.findByIdForUpdate(healthyOrderId)).thenReturn(Optional.of(healthyOrder));
        when(refundRepository.sumCompletedAmountByOrderId(healthyOrderId)).thenReturn(0L, 800L);
        when(paymentRepository.findByOrderId(healthyOrderId)).thenReturn(List.of(payment("mock_ref_healthy")));
        when(paymentGatewayClient.refund(eq("mock_ref_healthy"), any())).thenReturn(PaymentResult.success("mock_refund_healthy"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.findByOrderId(healthyOrderId)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.refundAllForEventCancellation(eventId, UUID.randomUUID());

        // The broken order's failure was swallowed; the healthy order was still refunded.
        verify(refundRepository, times(1)).save(any(Refund.class));
        verify(paymentGatewayClient, times(1)).refund(anyString(), any());
    }

    @Test
    void refundAllForEventCancellation_orderWithNoRemainingBalance_isSkipped() {
        when(ticketRepository.findDistinctOrderIdsByEventId(eventId)).thenReturn(List.of(orderId));
        Order order = order(1000L, OrderStatus.PARTIALLY_REFUNDED);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(1000L); // already fully refunded via prior partials

        service.refundAllForEventCancellation(eventId, UUID.randomUUID());

        verifyNoInteractions(paymentGatewayClient, paymentRepository);
        verify(refundRepository, never()).save(any());
    }

    // ---- BR-NOTIFY-001 (Phase 11): REFUND_CONFIRMATION fires on any successful refund, never on a decline ----

    @Test
    void createRefund_fullRefund_firesRefundConfirmationNotification_toTheBuyer() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 1000L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_notify"));
        UUID savedRefundId = UUID.randomUUID();
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> {
            Refund r = inv.getArgument(0);
            r.setId(savedRefundId);
            return r;
        });
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createRefund(orderId, requestWithReason("full refund"));

        verify(notificationService).notify(buyerId, NotificationType.REFUND_CONFIRMATION, "Refund", savedRefundId);
    }

    @Test
    void createRefund_partialRefund_alsoFiresRefundConfirmationNotification() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 400L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_partial_notify"));
        UUID savedRefundId = UUID.randomUUID();
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> {
            Refund r = inv.getArgument(0);
            r.setId(savedRefundId);
            return r;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundCreateRequest request = new RefundCreateRequest(new MoneyDto(400L, "USD"), "partial refund");
        service.createRefund(orderId, request);

        verify(notificationService).notify(buyerId, NotificationType.REFUND_CONFIRMATION, "Refund", savedRefundId);
    }

    @Test
    void createRefund_gatewayDeclined_doesNotFireAnyNotification() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("gwref_fail_notify")));
        when(paymentGatewayClient.refund(eq("gwref_fail_notify"), any())).thenReturn(PaymentResult.failure("declined"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createRefund(orderId, requestWithReason("will be declined"));

        verifyNoInteractions(notificationService);
    }

    // ---- BR-WAIT-002/003 (Phase 11): a full refund of a GA ticket restocks inventory and offers it to the waitlist ----

    @Test
    void createRefund_fullRefund_gaTicket_restocksQuantityAvailable_andNotifiesWaitlist() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 1000L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_ga"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        Ticket gaTicket = ticket(UUID.randomUUID(), orderId); // seatId null -> GA
        UUID ticketTypeId = gaTicket.getTicketTypeId();
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of(gaTicket));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        TicketType ticketType = TicketType.builder().id(ticketTypeId).eventId(eventId).quantityAvailable(0).build();
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(ticketType));

        service.createRefund(orderId, requestWithReason("full refund, GA ticket"));

        assertThat(ticketType.getQuantityAvailable()).isEqualTo(1);
        verify(ticketTypeRepository).save(ticketType);
        verify(waitlistService).notifyNextInLineIfAvailable(eventId, ticketTypeId);
    }

    /**
     * Reserved-seating {@code TicketType}s never had {@code quantityAvailable}
     * decremented at checkout in the first place (only {@code Seat.status}
     * flips) - restocking it here for a seated ticket's refund would
     * fabricate inventory that was never actually reserved via that
     * counter. Confirms the scope restriction is real, not just documented.
     */
    @Test
    void createRefund_fullRefund_seatedTicket_doesNotRestockOrNotifyWaitlist() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 1000L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_seated"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        Ticket seatedTicket = ticket(UUID.randomUUID(), orderId);
        seatedTicket.setSeatId(UUID.randomUUID());
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of(seatedTicket));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createRefund(orderId, requestWithReason("full refund, seated ticket"));

        assertThat(seatedTicket.getStatus()).isEqualTo(TicketStatus.REFUNDED);
        verifyNoInteractions(ticketTypeRepository, waitlistService);
    }

    @Test
    void createRefund_partialRefund_doesNotTouchWaitlistOrTicketTypeInventory() {
        Order order = order(1000L, OrderStatus.PAID);
        stubOrderAndEventLookup(order);
        stubOrganizerCaller(UUID.randomUUID());
        when(refundRepository.sumCompletedAmountByOrderId(orderId)).thenReturn(0L, 400L);
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.CUSTOM).build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment("mock_ref")));
        when(paymentGatewayClient.refund(eq("mock_ref"), any())).thenReturn(PaymentResult.success("mock_refund_partial_ga"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundCreateRequest request = new RefundCreateRequest(new MoneyDto(400L, "USD"), "partial refund");
        service.createRefund(orderId, request);

        verifyNoInteractions(ticketTypeRepository, waitlistService);
    }
}
