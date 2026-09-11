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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} coverage for {@code GET/POST
 * /orders/{orderId}/refunds} against real Postgres + real signed JWTs. Sets
 * up real {@code Order}/{@code Payment}/{@code Ticket} rows directly (no
 * checkout precedent needed — refund's own machinery is what's under test)
 * and verifies {@code orders}/{@code tickets}/{@code refunds} table state by
 * reading straight from Postgres afterward, not just trusting the response
 * body.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RefundIntegrationTest {

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
                .name("Refund Test Org " + UUID.randomUUID())
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
                .title("Refund Test Event")
                .description("desc")
                .category("music")
                .status(EventStatus.PUBLISHED)
                .ticketPrefix("R" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
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
                .ownerId(ownerId).ticketNumber("R-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
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

    private void persistRefundPolicy(UUID eventId, RefundRuleType ruleType, Integer days) {
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(ruleType).daysBeforeEvent(days).build();
        RefundPolicy saved = refundPolicyRepository.save(policy);
        createdPolicyIds.add(saved.getId());
    }

    private void trackAnyRefundsForOrder(UUID orderId) {
        for (var r : refundRepository.findByOrderId(orderId)) {
            createdRefundIds.add(r.getId());
        }
    }

    // ---- create(): success paths ----

    @Test
    void create_owner_fullRefund_returns201_marksOrderRefundedAndTicketsRefundedInPostgres() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM, null);
        Order order = persistOrder(buyerId, orgId, 1000L, OrderStatus.PAID);
        Ticket ticket = persistTicket(eventId, ticketTypeId, buyerId, order.getId());
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String ownerToken = jwtService.generateAccessToken(owner);

        // Tracked for cleanup BEFORE any assertion runs - a chained
        // .andExpect(...) that fails partway would otherwise skip the
        // tracking call entirely and orphan the Refund row this request
        // creates (the HTTP call itself completes, and the row is written,
        // before the response assertions are even evaluated).
        MvcResult result = mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"attendee cannot attend\"}"))
                .andReturn();
        trackAnyRefundsForOrder(order.getId());
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"COMPLETED\"");

        // Real-DB verification.
        Order refreshedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(refreshedOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        Ticket refreshedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(refreshedTicket.getStatus()).isEqualTo(TicketStatus.REFUNDED);
        assertThat(refundRepository.findByOrderId(order.getId())).hasSize(1);
        assertThat(refundRepository.findByOrderId(order.getId()).get(0).getStatus().name()).isEqualTo("COMPLETED");
    }

    @Test
    void create_organizer_partialRefund_returns201_marksOrderPartiallyRefunded_ticketsStayValid() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User organizer = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM, null);
        Order order = persistOrder(buyerId, orgId, 1000L, OrderStatus.PAID);
        Ticket ticket = persistTicket(eventId, ticketTypeId, buyerId, order.getId());
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String organizerToken = jwtService.generateAccessToken(organizer);

        MvcResult result = mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"partial goodwill refund\",\"amount\":{\"amount\":400,\"currency\":\"USD\"}}"))
                .andReturn();
        trackAnyRefundsForOrder(order.getId());
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"COMPLETED\"", "\"amount\":400");

        Order refreshedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(refreshedOrder.getStatus()).isEqualTo(OrderStatus.PARTIALLY_REFUNDED);
        Ticket refreshedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(refreshedTicket.getStatus()).isEqualTo(TicketStatus.VALID);
    }

    @Test
    void create_gatewayDeclinedRefund_returns201WithFailedStatus_orderAndTicketsUntouched() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM, null);
        Order order = persistOrder(buyerId, orgId, 1000L, OrderStatus.PAID);
        Ticket ticket = persistTicket(eventId, ticketTypeId, buyerId, order.getId());
        persistPayment(order.getId(), "gwref_fail_" + UUID.randomUUID(), 1000L); // gatewayRef containing "fail" -> deterministic decline
        String ownerToken = jwtService.generateAccessToken(owner);

        MvcResult result = mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"gateway will decline this\"}"))
                .andReturn();
        trackAnyRefundsForOrder(order.getId());
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"FAILED\"");

        Order refreshedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(refreshedOrder.getStatus()).isEqualTo(OrderStatus.PAID); // unchanged
        Ticket refreshedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(refreshedTicket.getStatus()).isEqualTo(TicketStatus.VALID); // unchanged
        assertThat(refundRepository.findByOrderId(order.getId())).hasSize(1);
    }

    // ---- create(): BR-PAY-002 authorization ----

    @Test
    void create_buyerAttemptsSelfRefund_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM, null);
        Order order = persistOrder(buyer.getId(), orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyer.getId(), order.getId());
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String buyerToken = jwtService.generateAccessToken(buyer);

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"I want my money back\"}"))
                .andExpect(status().isForbidden());
        assertThat(refundRepository.findByOrderId(order.getId())).isEmpty();
    }

    @Test
    void create_roselessStranger_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM, null);
        Order order = persistOrder(buyerId, orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyerId, order.getId());
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String strangerToken = jwtService.generateAccessToken(stranger);

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"not my order\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"reason\":\"anonymous attempt\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- create(): BR-PAY-003 policy gate ----

    @Test
    void create_noRefundPolicyConfigured_returns409() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        // No refund policy row at all for this event.
        Order order = persistOrder(buyerId, orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyerId, order.getId());
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"no policy configured\"}"))
                .andExpect(status().isConflict());
        assertThat(refundRepository.findByOrderId(order.getId())).isEmpty();
    }

    // ---- create(): amount validation ----

    @Test
    void create_amountExceedsRemainingBalance_returns400() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM, null);
        Order order = persistOrder(buyerId, orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyerId, order.getId());
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"too much\",\"amount\":{\"amount\":1001,\"currency\":\"USD\"}}"))
                .andExpect(status().isBadRequest());
        assertThat(refundRepository.findByOrderId(order.getId())).isEmpty();
    }

    @Test
    void create_amountCurrencyMismatch_returns400() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM, null);
        Order order = persistOrder(buyerId, orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyerId, order.getId());
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"wrong currency\",\"amount\":{\"amount\":500,\"currency\":\"EUR\"}}"))
                .andExpect(status().isBadRequest());
        assertThat(refundRepository.findByOrderId(order.getId())).isEmpty();
    }

    @Test
    void create_unknownOrder_returns404() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", UUID.randomUUID())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"unknown order\"}"))
                .andExpect(status().isNotFound());
    }

    // ---- list(): visibility ----

    @Test
    void list_owningBuyer_returns200_withRefunds() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM, null);
        Order order = persistOrder(buyer.getId(), orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyer.getId(), order.getId());
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String ownerToken = jwtService.generateAccessToken(owner);
        MvcResult seedResult = mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"seed a refund\"}"))
                .andReturn();
        trackAnyRefundsForOrder(order.getId());
        assertThat(seedResult.getResponse().getStatus()).isEqualTo(201);

        String buyerToken = jwtService.generateAccessToken(buyer);
        mockMvc.perform(get("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(order.getId().toString()));
    }

    @Test
    void list_organizer_returns200() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User organizer = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        grantOrgRole(organizer.getId(), orgId, OrganizationRole.ORGANIZER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        Order order = persistOrder(buyerId, orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyerId, order.getId());

        String organizerToken = jwtService.generateAccessToken(organizer);
        mockMvc.perform(get("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void list_roselessStranger_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User stranger = inMemoryUser(Role.CUSTOMER);
        UUID buyerId = UUID.randomUUID();
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistTicketType(eventId);
        Order order = persistOrder(buyerId, orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyerId, order.getId());

        String strangerToken = jwtService.generateAccessToken(stranger);
        mockMvc.perform(get("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unknownOrder_returns404() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        String ownerToken = jwtService.generateAccessToken(owner);

        mockMvc.perform(get("/api/v1/orders/{orderId}/refunds", UUID.randomUUID())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }
}
