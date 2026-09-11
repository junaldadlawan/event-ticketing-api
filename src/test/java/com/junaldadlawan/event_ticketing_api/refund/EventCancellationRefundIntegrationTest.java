package com.junaldadlawan.event_ticketing_api.refund;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.entity.Payment;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.enums.PaymentStatus;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PaymentRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.refund.entity.Refund;
import com.junaldadlawan.event_ticketing_api.refund.enums.RefundStatus;
import com.junaldadlawan.event_ticketing_api.refund.repository.RefundRepository;
import com.junaldadlawan.event_ticketing_api.refundpolicy.entity.RefundPolicy;
import com.junaldadlawan.event_ticketing_api.refundpolicy.enums.RefundRuleType;
import com.junaldadlawan.event_ticketing_api.refundpolicy.repository.RefundPolicyRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} proof of BR-PAY-005: cancelling a real event
 * with real paid orders on it, via the real {@code POST
 * /events/{eventId}/cancel} HTTP endpoint (never calling any refund endpoint
 * directly), and confirming {@code refunds}/{@code orders}/{@code tickets}
 * all end up in the correct state — proving the {@code EventServiceImpl
 * .cancelEvent} → {@code RefundService.refundAllForEventCancellation} wiring
 * actually fires end-to-end, not just that a Mockito-mocked unit test
 * verified a method call (see {@code EventServiceImplTest
 * .cancelEvent_success_triggersRefundAllForEventCancellation} for that
 * narrower proof).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventCancellationRefundIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private RefundPolicyRepository refundPolicyRepository;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTicketTypeIds = new ArrayList<>();
    private final List<UUID> createdTicketIds = new ArrayList<>();
    private final List<UUID> createdOrderIds = new ArrayList<>();
    private final List<UUID> createdPaymentIds = new ArrayList<>();
    private final List<UUID> createdRefundIds = new ArrayList<>();
    private final List<UUID> createdPolicyIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdRefundIds) {
            refundRepository.deleteById(id);
        }
        createdRefundIds.clear();
        for (UUID id : createdPaymentIds) {
            paymentRepository.deleteById(id);
        }
        createdPaymentIds.clear();
        for (UUID id : createdOrderIds) {
            orderRepository.deleteById(id);
        }
        createdOrderIds.clear();
        for (UUID id : createdTicketIds) {
            ticketRepository.deleteById(id);
        }
        createdTicketIds.clear();
        for (UUID id : createdTicketTypeIds) {
            ticketTypeRepository.deleteById(id);
        }
        createdTicketTypeIds.clear();
        for (UUID id : createdPolicyIds) {
            refundPolicyRepository.deleteById(id);
        }
        createdPolicyIds.clear();
        for (UUID id : createdEventIds) {
            eventRepository.deleteById(id);
        }
        createdEventIds.clear();
        for (OrganizationMember member : createdMembers) {
            organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                    .ifPresent(organizationMemberRepository::delete);
        }
        createdMembers.clear();
        for (UUID id : createdOrgIds) {
            organizationRepository.deleteById(id);
        }
        createdOrgIds.clear();
    }

    // ---- fixtures ----

    private User inMemoryUser(Role role) {
        return User.builder().id(UUID.randomUUID()).email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Cancel-Refund Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED)
                .ownerId(ownerId)
                .documents(List.of())
                .build();
        Organization saved = organizationRepository.save(organization);
        createdOrgIds.add(saved.getId());
        return saved.getId();
    }

    private void grantOrgRole(UUID userId, UUID organizationId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(organizationId).build();
        member.getRoles().add(role);
        createdMembers.add(organizationMemberRepository.save(member));
    }

    private UUID persistEvent(UUID organizationId) {
        Instant startAt = Instant.now().plus(20, ChronoUnit.DAYS);
        Event event = Event.builder()
                .organizationId(organizationId)
                .title("Cancel-Refund Test Event")
                .description("desc")
                .category("music")
                .status(EventStatus.PUBLISHED)
                .ticketPrefix("C" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistTicketType(UUID eventId) {
        TicketType tt = TicketType.builder()
                .eventId(eventId).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .quantityTotal(10).quantityAvailable(10)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10).build();
        TicketType saved = ticketTypeRepository.save(tt);
        createdTicketTypeIds.add(saved.getId());
        return saved.getId();
    }

    private Ticket persistTicket(UUID eventId, UUID ticketTypeId, UUID ownerId, UUID orderId) {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .id(ticketId).orderId(orderId).eventId(eventId).ticketTypeId(ticketTypeId).seatId(null)
                .ownerId(ownerId).ticketNumber("C-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential("cred-" + ticketId).credentialVersion(0).status(TicketStatus.VALID)
                .build();
        Ticket saved = ticketRepository.save(ticket);
        createdTicketIds.add(saved.getId());
        return saved;
    }

    private Order persistOrder(UUID buyerId, UUID orgId, long totalAmount, OrderStatus status) {
        Order order = Order.builder()
                .buyerId(buyerId).payeeType(PayeeType.ORGANIZATION).payeeId(orgId)
                .status(status).total(Money.builder().amount(totalAmount).currency("USD").build())
                .createdBy(buyerId.toString())
                .build();
        Order saved = orderRepository.save(order);
        createdOrderIds.add(saved.getId());
        return saved;
    }

    private void persistPayment(UUID orderId, String gatewayRef, long amount) {
        Payment payment = Payment.builder()
                .orderId(orderId).gatewayRef(gatewayRef)
                .amount(Money.builder().amount(amount).currency("USD").build())
                .status(PaymentStatus.COMPLETED)
                .build();
        Payment saved = paymentRepository.save(payment);
        createdPaymentIds.add(saved.getId());
    }

    private void trackAnyRefundsForOrder(UUID orderId) {
        for (Refund r : refundRepository.findByOrderId(orderId)) {
            createdRefundIds.add(r.getId());
        }
    }

    // ---- BR-PAY-005: cancellation bypasses the refund policy entirely ----

    @Test
    void cancelEvent_withNoRefundPolicyConfigured_stillRefundsEveryTicketHolder() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        // Deliberately NO RefundPolicy row - the direct-refund endpoint would
        // 409 here (see RefundIntegrationTest.create_noRefundPolicyConfigured_returns409),
        // but cancellation must refund regardless.
        Order order = persistOrder(buyerId, orgId, 1000L, OrderStatus.PAID);
        Ticket ticket = persistTicket(eventId, ticketTypeId, buyerId, order.getId());
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String ownerToken = jwtService.generateAccessToken(owner);

        var result = mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andReturn();
        // Tracked BEFORE asserting the status code so tearDown still cleans
        // up any Refund row the request produced even if an assertion below
        // fails partway through (a bare .andExpect(...) chained directly on
        // perform() would skip this line entirely on failure, orphaning the
        // row - this bit the first draft of this test).
        trackAnyRefundsForOrder(order.getId());
        assertThat(result.getResponse().getStatus()).isEqualTo(202);

        Event refreshedEvent = eventRepository.findById(eventId).orElseThrow();
        assertThat(refreshedEvent.getStatus()).isEqualTo(EventStatus.CANCELLED);

        List<Refund> refunds = refundRepository.findByOrderId(order.getId());
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refunds.get(0).getAmount().getAmount()).isEqualTo(1000L);
        assertThat(refunds.get(0).getReason()).isEqualTo("Event cancelled");
        assertThat(refunds.get(0).getInitiatedBy()).isEqualTo(owner.getId());

        Order refreshedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(refreshedOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        Ticket refreshedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(refreshedTicket.getStatus()).isEqualTo(TicketStatus.REFUNDED);
    }

    @Test
    void cancelEvent_withNoRefundsPolicyExplicitlyConfigured_stillBypassesItAndRefunds() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        RefundPolicy noRefunds = RefundPolicy.builder().eventId(eventId).ruleType(RefundRuleType.NO_REFUNDS).build();
        createdPolicyIds.add(refundPolicyRepository.save(noRefunds).getId());
        Order order = persistOrder(buyerId, orgId, 500L, OrderStatus.PAID);
        Ticket ticket = persistTicket(eventId, ticketTypeId, buyerId, order.getId());
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 500L);
        String ownerToken = jwtService.generateAccessToken(owner);

        var result = mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andReturn();
        trackAnyRefundsForOrder(order.getId());
        assertThat(result.getResponse().getStatus()).isEqualTo(202);

        List<Refund> refunds = refundRepository.findByOrderId(order.getId());
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        Order refreshedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(refreshedOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        Ticket refreshedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(refreshedTicket.getStatus()).isEqualTo(TicketStatus.REFUNDED);
    }

    // ---- best-effort: one order's failure doesn't stop the rest ----

    @Test
    void cancelEvent_multipleOrders_oneWithNoPaymentRow_bestEffortStillRefundsTheOther() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID buyerAId = UUID.randomUUID();
        UUID buyerBId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);

        // Order A: healthy, has a Payment row -> should be refunded.
        Order orderA = persistOrder(buyerAId, orgId, 1000L, OrderStatus.PAID);
        Ticket ticketA = persistTicket(eventId, ticketTypeId, buyerAId, orderA.getId());
        persistPayment(orderA.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);

        // Order B: data-inconsistent, no Payment row at all -> issueRefund
        // throws ResourceNotFoundException internally, must not abort A.
        Order orderB = persistOrder(buyerBId, orgId, 750L, OrderStatus.PAID);
        Ticket ticketB = persistTicket(eventId, ticketTypeId, buyerBId, orderB.getId());

        String ownerToken = jwtService.generateAccessToken(owner);

        var result = mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andReturn();
        trackAnyRefundsForOrder(orderA.getId());
        trackAnyRefundsForOrder(orderB.getId());
        assertThat(result.getResponse().getStatus()).isEqualTo(202);

        // Order A refunded successfully.
        Order refreshedOrderA = orderRepository.findById(orderA.getId()).orElseThrow();
        assertThat(refreshedOrderA.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        Ticket refreshedTicketA = ticketRepository.findById(ticketA.getId()).orElseThrow();
        assertThat(refreshedTicketA.getStatus()).isEqualTo(TicketStatus.REFUNDED);
        assertThat(refundRepository.findByOrderId(orderA.getId())).hasSize(1);

        // Order B's failure was swallowed - left untouched, no Refund row.
        Order refreshedOrderB = orderRepository.findById(orderB.getId()).orElseThrow();
        assertThat(refreshedOrderB.getStatus()).isEqualTo(OrderStatus.PAID);
        Ticket refreshedTicketB = ticketRepository.findById(ticketB.getId()).orElseThrow();
        assertThat(refreshedTicketB.getStatus()).isEqualTo(TicketStatus.VALID);
        assertThat(refundRepository.findByOrderId(orderB.getId())).isEmpty();
    }

    // ---- already-refunded orders are skipped, not double-refunded ----

    @Test
    void cancelEvent_orderAlreadyRefunded_isSkipped_noDuplicateRefundIssued() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        Order order = persistOrder(buyerId, orgId, 1000L, OrderStatus.REFUNDED); // already refunded via a prior direct refund
        persistTicket(eventId, ticketTypeId, buyerId, order.getId());
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isAccepted());

        assertThat(refundRepository.findByOrderId(order.getId())).isEmpty();
    }
}
