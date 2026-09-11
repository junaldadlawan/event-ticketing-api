package com.junaldadlawan.event_ticketing_api.notification;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.notification.entity.Notification;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationChannel;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationStatus;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.repository.NotificationRepository;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.entity.Payment;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.enums.PaymentStatus;
import com.junaldadlawan.event_ticketing_api.order.repository.CheckoutIdempotencyKeyRepository;
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
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import com.junaldadlawan.event_ticketing_api.waitlist.entity.WaitlistEntry;
import com.junaldadlawan.event_ticketing_api.waitlist.repository.WaitlistEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

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
 * Full {@code @SpringBootTest} coverage for Phase 11 (Notifications) against
 * real Postgres + real signed JWTs + the real filter chain. Covers: {@code
 * GET /users/me/notifications} (own-only, most-recent-first, the new
 * {@code SecurityConfig} matcher, anonymous 401), the NFR 5.2 end-to-end
 * proof (a real checkout completing successfully despite a delivery
 * failure), checkout/refund/event-cancellation trigger counts and content,
 * and the full waitlist restock-and-notify chain (BR-WAIT-002/003) driven
 * by a real refund.
 * <p>
 * Every notified user in this file is a real, persisted {@code User} row
 * (unlike some earlier phases' {@code inMemoryUser} JWT-only fixtures) -
 * {@code NotificationServiceImpl.notify} looks the recipient up via {@code
 * UserRepository} to resolve an email, so an unpersisted buyer would always
 * land on the FAILED/no-such-user branch instead of the SENT branch this
 * file needs to exercise for its content assertions.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
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
    private RefundPolicyRepository refundPolicyRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private CheckoutIdempotencyKeyRepository checkoutIdempotencyKeyRepository;
    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTicketTypeIds = new ArrayList<>();
    private final List<UUID> createdTicketIds = new ArrayList<>();
    private final List<UUID> createdOrderIds = new ArrayList<>();
    private final List<UUID> createdPaymentIds = new ArrayList<>();
    private final List<UUID> createdRefundIds = new ArrayList<>();
    private final List<UUID> createdPolicyIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdCartIds = new ArrayList<>();
    private final List<UUID> createdCartItemIds = new ArrayList<>();
    private final List<UUID> createdIdempotencyKeyIds = new ArrayList<>();
    private final List<UUID> createdWaitlistEntryIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        // Every notification row this file's triggers create is scoped to a
        // userId this test itself persisted - sweep by that, since the
        // individual Notification ids are never handed back to the caller.
        for (UUID userId : createdUserIds) {
            for (Notification n : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
                notificationRepository.deleteById(n.getId());
            }
        }
        for (UUID id : createdWaitlistEntryIds) {
            waitlistEntryRepository.deleteById(id);
        }
        createdWaitlistEntryIds.clear();
        for (UUID id : createdRefundIds) {
            refundRepository.deleteById(id);
        }
        createdRefundIds.clear();
        for (UUID id : createdIdempotencyKeyIds) {
            checkoutIdempotencyKeyRepository.deleteById(id);
        }
        createdIdempotencyKeyIds.clear();
        for (UUID id : createdCartItemIds) {
            cartItemRepository.deleteById(id);
        }
        createdCartItemIds.clear();
        for (UUID id : createdCartIds) {
            cartRepository.deleteById(id);
        }
        createdCartIds.clear();
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
        for (UUID id : createdPolicyIds) {
            refundPolicyRepository.deleteById(id);
        }
        createdPolicyIds.clear();
        for (UUID id : createdTicketTypeIds) {
            ticketTypeRepository.deleteById(id);
        }
        createdTicketTypeIds.clear();
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
        for (UUID id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
    }

    // ---- fixtures ----

    private User persistUser(String emailLocalPart) {
        User user = User.builder()
                .name("Notification Test User")
                .email(emailLocalPart + "-" + UUID.randomUUID() + "@test.local")
                .passwordHash("irrelevant")
                .role(Role.CUSTOMER)
                .build();
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Notification Test Org " + UUID.randomUUID())
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
                .title("Notification Test Event")
                .description("desc")
                .category("music")
                .status(EventStatus.PUBLISHED)
                .ticketPrefix("N" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistGaTicketType(UUID eventId, int quantityAvailable, int quantityTotal) {
        TicketType tt = TicketType.builder()
                .eventId(eventId).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .quantityTotal(quantityTotal).quantityAvailable(quantityAvailable)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10).build();
        TicketType saved = ticketTypeRepository.save(tt);
        createdTicketTypeIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistReservedTicketType(UUID eventId) {
        TicketType tt = TicketType.builder()
                .eventId(eventId).name("Reserved").kind(TicketTypeKind.RESERVED_SEATING)
                .price(Money.builder().amount(2000L).currency("USD").build())
                .quantityTotal(5).quantityAvailable(5)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10).build();
        TicketType saved = ticketTypeRepository.save(tt);
        createdTicketTypeIds.add(saved.getId());
        return saved.getId();
    }

    private Ticket persistTicket(UUID eventId, UUID ticketTypeId, UUID ownerId, UUID orderId, UUID seatId) {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .id(ticketId).orderId(orderId).eventId(eventId).ticketTypeId(ticketTypeId).seatId(seatId)
                .ownerId(ownerId).ticketNumber("N-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
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

    private void persistRefundPolicy(UUID eventId, RefundRuleType ruleType) {
        RefundPolicy policy = RefundPolicy.builder().eventId(eventId).ruleType(ruleType).build();
        RefundPolicy saved = refundPolicyRepository.save(policy);
        createdPolicyIds.add(saved.getId());
    }

    private UUID createCart(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/carts").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        UUID cartId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdCartIds.add(cartId);
        return cartId;
    }

    private void addGaItem(String token, UUID cartId, UUID ticketTypeId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        for (var item : objectMapper.readTree(result.getResponse().getContentAsString()).get("items")) {
            createdCartItemIds.add(UUID.fromString(item.get("id").asText()));
        }
    }

    private MvcResult checkout(String token, UUID cartId, UUID idempotencyKey) throws Exception {
        createdIdempotencyKeyIds.add(idempotencyKey);
        return mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andReturn();
    }

    private void trackOrderAndPaymentFromResponse(MvcResult result) throws Exception {
        var idNode = objectMapper.readTree(result.getResponse().getContentAsString()).get("id");
        if (idNode == null) {
            return;
        }
        UUID orderId = UUID.fromString(idNode.asText());
        createdOrderIds.add(orderId);
        for (Payment payment : paymentRepository.findByOrderId(orderId)) {
            createdPaymentIds.add(payment.getId());
        }
        for (Ticket ticket : ticketRepository.findByOrderId(orderId)) {
            createdTicketIds.add(ticket.getId());
        }
    }

    private List<Notification> notificationsFor(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private void trackAnyRefundsForOrder(UUID orderId) {
        for (var r : refundRepository.findByOrderId(orderId)) {
            createdRefundIds.add(r.getId());
        }
    }

    // ---- GET /users/me/notifications: own-only, most-recent-first, SecurityConfig matcher ----

    @Test
    void list_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_authenticatedNonAdminUser_returns200_notForbidden_ownNotificationsOnly_mostRecentFirst() throws Exception {
        User caller = persistUser("caller");
        User otherUser = persistUser("other");
        String callerToken = jwtService.generateAccessToken(caller);

        // Two notifications for the caller, persisted in sequence so their
        // createdAt values are genuinely distinct - proves ORDER BY
        // created_at DESC, not just "returns everything".
        Notification olderForCaller = notificationRepository.save(Notification.builder()
                .userId(caller.getId()).type(NotificationType.ORDER_CONFIRMATION).channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.SENT).sentAt(Instant.now()).build());
        Thread.sleep(20);
        Notification newerForCaller = notificationRepository.save(Notification.builder()
                .userId(caller.getId()).type(NotificationType.PAYMENT_RECEIPT).channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.SENT).sentAt(Instant.now()).build());
        // A notification belonging to a DIFFERENT user - must never leak into caller's list.
        notificationRepository.save(Notification.builder()
                .userId(otherUser.getId()).type(NotificationType.REFUND_CONFIRMATION).channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.SENT).sentAt(Instant.now()).build());

        mockMvc.perform(get("/api/v1/users/me/notifications").header("Authorization", "Bearer " + callerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newerForCaller.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(olderForCaller.getId().toString()));
    }

    // ---- NFR 5.2 headline proof: a real checkout completes successfully despite a FAILED notification ----

    @Test
    void checkout_buyerEmailTriggersDeliveryFailure_orderStillCompletesSuccessfully_notificationsRecordedAsFailed() throws Exception {
        User owner = persistUser("owner");
        User buyer = persistUser("buyer-fail-delivery"); // MockEmailSender: any "fail-delivery" recipient always fails
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistGaTicketType(eventId, 5, 5);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);
        addGaItem(buyerToken, cartId, ticketTypeId);

        MvcResult result = checkout(buyerToken, cartId, UUID.randomUUID());
        trackOrderAndPaymentFromResponse(result);

        // The order itself succeeded completely, unaffected by the doomed notification.
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        UUID orderId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        List<Ticket> tickets = ticketRepository.findByOrderId(orderId);
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).getStatus()).isEqualTo(TicketStatus.VALID);

        // Both notifications were attempted and recorded as FAILED - the
        // failure was fully isolated, never rolled back the checkout above.
        List<Notification> notifications = notificationsFor(buyer.getId());
        assertThat(notifications).hasSize(2);
        assertThat(notifications).allSatisfy(n -> {
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(n.getSentAt()).isNull();
        });
        assertThat(notifications).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.ORDER_CONFIRMATION, NotificationType.PAYMENT_RECEIPT);
    }

    /**
     * Regression test for the code-reviewer CRITICAL finding: {@code
     * NotificationServiceImpl.notify} originally had no propagation
     * boundary of its own, so its {@code saveAndFlush} calls only JOINED
     * whatever ambient transaction was already open (e.g. mid-checkout). A
     * DB-level failure on the {@code Notification} write itself (not just
     * an {@code EmailSender} delivery failure - the case covered by
     * {@code checkout_buyerEmailTriggersDeliveryFailure_...} above) would
     * mark that AMBIENT transaction rollback-only the instant it was
     * thrown, before {@code notify()}'s own try/catch ever ran - meaning
     * the checkout's Order/Payment/Ticket rows would be silently rolled
     * back despite the card already being charged, and despite {@code
     * checkout()} appearing to return normally. Fixed by making {@code
     * notify()} {@code @Transactional(propagation = REQUIRES_NEW)}, the
     * same idiom already used by {@code WaitlistPositionAssigner#assign}
     * for an identical class of problem.
     * <p>
     * A live end-to-end reproduction (forcing a genuine Postgres
     * constraint violation mid-checkout via a spied {@code
     * NotificationRepository}) was attempted and abandoned: {@code
     * invocation.callRealMethod()} on a Mockito spy of an INTERFACE-typed
     * Spring Data repository throws {@code MockitoException: Cannot call
     * abstract real method on java object} - a genuine Mockito limitation
     * for JDK-dynamic-proxy-backed interfaces, not something reachable
     * from application code. Verified by hand: with that broken approach,
     * the test passed identically whether the fix was present or removed
     * (the MockitoException itself was what got caught by {@code
     * notify()}'s try/catch in both cases, never a real DB exception), so
     * it would have been a false-positive regression test. This structural
     * test instead pins the actual fix directly via reflection - it fails
     * immediately if the annotation is ever removed or its propagation
     * changed, without depending on reproducing Spring's internal
     * rollback-only marking end-to-end.
     */
    @Test
    void notify_isAnnotatedRequiresNew_soADbFailureCannotPoisonTheCallersTransaction() throws Exception {
        var method = com.junaldadlawan.event_ticketing_api.notification.service.NotificationServiceImpl.class
                .getMethod("notify", UUID.class,
                        com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType.class,
                        String.class, UUID.class);
        var transactional = method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(transactional).as("notify() must be @Transactional to own a propagation boundary").isNotNull();
        assertThat(transactional.propagation())
                .as("must be REQUIRES_NEW - a plain/REQUIRED-propagation write here would join and could poison the caller's transaction")
                .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW);
    }

    // ---- checkout: exactly 2 notifications on a fresh checkout, replay fires none extra ----

    @Test
    void checkout_freshCheckout_firesExactlyTwoNotifications_replayWithSameKeyFiresNoMore() throws Exception {
        User owner = persistUser("owner");
        User buyer = persistUser("buyer");
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistGaTicketType(eventId, 5, 5);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);
        addGaItem(buyerToken, cartId, ticketTypeId);
        UUID idempotencyKey = UUID.randomUUID();

        MvcResult firstResult = checkout(buyerToken, cartId, idempotencyKey);
        trackOrderAndPaymentFromResponse(firstResult);
        assertThat(firstResult.getResponse().getStatus()).isEqualTo(201);

        List<Notification> afterFirstCheckout = notificationsFor(buyer.getId());
        assertThat(afterFirstCheckout).hasSize(2);
        assertThat(afterFirstCheckout).extracting(Notification::getStatus).containsOnly(NotificationStatus.SENT);
        assertThat(afterFirstCheckout).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.ORDER_CONFIRMATION, NotificationType.PAYMENT_RECEIPT);

        // Replay the identical request (same idempotency key) - an
        // already-completed checkout, not a new purchase.
        MvcResult replayResult = mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andReturn();
        assertThat(replayResult.getResponse().getStatus()).isEqualTo(201);

        assertThat(notificationsFor(buyer.getId())).hasSize(2);
    }

    // ---- refund: REFUND_CONFIRMATION on full and partial refund; never on a gateway decline ----

    @Test
    void refund_fullRefund_firesRefundConfirmation_sentSuccessfully() throws Exception {
        User owner = persistUser("owner");
        User buyer = persistUser("buyer");
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistGaTicketType(eventId, 4, 5);
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM);
        Order order = persistOrder(buyer.getId(), orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyer.getId(), order.getId(), null);
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String ownerToken = jwtService.generateAccessToken(owner);

        MvcResult refundResult = mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"attendee cannot attend\"}"))
                .andReturn();
        trackAnyRefundsForOrder(order.getId());
        assertThat(refundResult.getResponse().getStatus()).isEqualTo(201);

        List<Notification> notifications = notificationsFor(buyer.getId());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.REFUND_CONFIRMATION);
        assertThat(notifications.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void refund_partialRefund_alsoFiresRefundConfirmation() throws Exception {
        User owner = persistUser("owner");
        User buyer = persistUser("buyer");
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistGaTicketType(eventId, 4, 5);
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM);
        Order order = persistOrder(buyer.getId(), orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyer.getId(), order.getId(), null);
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String ownerToken = jwtService.generateAccessToken(owner);

        MvcResult refundResult = mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"partial goodwill refund\",\"amount\":{\"amount\":400,\"currency\":\"USD\"}}"))
                .andReturn();
        trackAnyRefundsForOrder(order.getId());
        assertThat(refundResult.getResponse().getStatus()).isEqualTo(201);

        List<Notification> notifications = notificationsFor(buyer.getId());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.REFUND_CONFIRMATION);
    }

    @Test
    void refund_gatewayDeclined_firesNoNotificationAtAll() throws Exception {
        User owner = persistUser("owner");
        User buyer = persistUser("buyer");
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistGaTicketType(eventId, 4, 5);
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM);
        Order order = persistOrder(buyer.getId(), orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyer.getId(), order.getId(), null);
        // MockPaymentGatewayClient.refund: any gatewayRef containing "fail" is declined.
        persistPayment(order.getId(), "gwref_fail_" + UUID.randomUUID(), 1000L);
        String ownerToken = jwtService.generateAccessToken(owner);

        MvcResult refundResult = mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"will be declined\"}"))
                .andReturn();
        trackAnyRefundsForOrder(order.getId());
        assertThat(refundResult.getResponse().getStatus()).isEqualTo(201);
        assertThat(objectMapper.readTree(refundResult.getResponse().getContentAsString()).get("status").asText()).isEqualTo("FAILED");

        assertThat(notificationsFor(buyer.getId())).isEmpty();
    }

    // ---- event cancellation: EVENT_CANCELLATION to the CURRENT ticket owner, not the original buyer ----

    @Test
    void cancelEvent_ticketWasTransferredBeforeCancellation_notifiesCurrentOwner_notOriginalBuyer() throws Exception {
        User owner = persistUser("owner");
        User originalBuyer = persistUser("original-buyer");
        User currentOwner = persistUser("current-owner");
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistGaTicketType(eventId, 4, 5);
        Order order = persistOrder(originalBuyer.getId(), orgId, 1000L, OrderStatus.PAID);
        Ticket ticket = persistTicket(eventId, ticketTypeId, originalBuyer.getId(), order.getId(), null);
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String originalBuyerToken = jwtService.generateAccessToken(originalBuyer);
        String ownerToken = jwtService.generateAccessToken(owner);

        // Transfer the ticket away BEFORE the event is cancelled.
        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticket.getId())
                        .header("Authorization", "Bearer " + originalBuyerToken)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + currentOwner.getId() + "\"}"))
                .andExpect(status().isOk());
        Ticket refreshedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(refreshedTicket.getOwnerId()).isEqualTo(currentOwner.getId());

        MvcResult cancelResult = mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andReturn();
        trackAnyRefundsForOrder(order.getId());
        assertThat(cancelResult.getResponse().getStatus()).isEqualTo(202);

        // EVENT_CANCELLATION reaches the CURRENT owner, not the original buyer.
        List<Notification> currentOwnerNotifications = notificationsFor(currentOwner.getId());
        assertThat(currentOwnerNotifications).extracting(Notification::getType).contains(NotificationType.EVENT_CANCELLATION);
        assertThat(currentOwnerNotifications).extracting(Notification::getType).doesNotContain(NotificationType.REFUND_CONFIRMATION);

        // REFUND_CONFIRMATION (a separate notification) still reaches the
        // ORIGINAL buyer - the order's own refund is attributed to whoever
        // paid for it, regardless of who holds the ticket now.
        List<Notification> originalBuyerNotifications = notificationsFor(originalBuyer.getId());
        assertThat(originalBuyerNotifications).extracting(Notification::getType).contains(NotificationType.REFUND_CONFIRMATION);
        assertThat(originalBuyerNotifications).extracting(Notification::getType).doesNotContain(NotificationType.EVENT_CANCELLATION);
    }

    // ---- BR-WAIT-002/003 end-to-end: a GA refund restocks inventory and notifies the waitlist ----

    @Test
    void refund_gaTicket_restocksInventory_notifiesEarliestTicketTypeWaiter_andEarliestEventGeneralWaiter_independently() throws Exception {
        User owner = persistUser("owner");
        User buyer = persistUser("buyer");
        User specificWaiter1 = persistUser("specific-waiter-1");
        User specificWaiter2 = persistUser("specific-waiter-2");
        User generalWaiter = persistUser("general-waiter");
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistGaTicketType(eventId, 0, 3); // sold out
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM);

        // Two waiters on the ticket-type-specific waitlist, in join order.
        String specificToken1 = jwtService.generateAccessToken(specificWaiter1);
        mockMvc.perform(post("/api/v1/events/{eventId}/waitlist", eventId)
                        .header("Authorization", "Bearer " + specificToken1)
                        .contentType("application/json")
                        .content("{\"ticketTypeId\":\"" + ticketTypeId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1));
        String specificToken2 = jwtService.generateAccessToken(specificWaiter2);
        mockMvc.perform(post("/api/v1/events/{eventId}/waitlist", eventId)
                        .header("Authorization", "Bearer " + specificToken2)
                        .contentType("application/json")
                        .content("{\"ticketTypeId\":\"" + ticketTypeId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(2));
        // One event-general waiter (only ticket type in the event is sold out too).
        String generalToken = jwtService.generateAccessToken(generalWaiter);
        mockMvc.perform(post("/api/v1/events/{eventId}/waitlist", eventId)
                        .header("Authorization", "Bearer " + generalToken)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated());
        waitlistEntryRepository.findAll().stream()
                .filter(e -> e.getEventId().equals(eventId))
                .forEach(e -> createdWaitlistEntryIds.add(e.getId()));

        Order order = persistOrder(buyer.getId(), orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyer.getId(), order.getId(), null);
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String ownerToken = jwtService.generateAccessToken(owner);

        MvcResult refundResult = mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"attendee cannot attend\"}"))
                .andReturn();
        trackAnyRefundsForOrder(order.getId());
        assertThat(refundResult.getResponse().getStatus()).isEqualTo(201);

        // Inventory restocked by exactly 1.
        TicketType refreshedType = ticketTypeRepository.findById(ticketTypeId).orElseThrow();
        assertThat(refreshedType.getQuantityAvailable()).isEqualTo(1);

        // The FIRST-positioned ticket-type-specific waiter is notified...
        WaitlistEntry entry1 = waitlistEntryRepository.findByUserIdOrderByCreatedAtAsc(specificWaiter1.getId()).get(0);
        assertThat(entry1.getNotifiedAt()).isNotNull();
        assertThat(entry1.getOfferExpiresAt()).isNotNull();
        assertThat(notificationsFor(specificWaiter1.getId())).extracting(Notification::getType)
                .containsExactly(NotificationType.WAITLIST_AVAILABILITY);

        // ...the SECOND-positioned waiter is NOT (only one unit freed up).
        WaitlistEntry entry2 = waitlistEntryRepository.findByUserIdOrderByCreatedAtAsc(specificWaiter2.getId()).get(0);
        assertThat(entry2.getNotifiedAt()).isNull();
        assertThat(notificationsFor(specificWaiter2.getId())).isEmpty();

        // The event-general waiter is ALSO notified, independently, from
        // the same restock (restocking any one ticket type ends the
        // event's overall "generally sold out" state).
        WaitlistEntry generalEntry = waitlistEntryRepository.findByUserIdOrderByCreatedAtAsc(generalWaiter.getId()).get(0);
        assertThat(generalEntry.getNotifiedAt()).isNotNull();
        assertThat(notificationsFor(generalWaiter.getId())).extracting(Notification::getType)
                .containsExactly(NotificationType.WAITLIST_AVAILABILITY);
    }

    /**
     * The scope restriction, proven end-to-end: reserved-seating {@code
     * TicketType}s never had {@code quantityAvailable} decremented at
     * checkout, so a seated ticket's refund must not restock it or notify
     * anyone waiting.
     */
    @Test
    void refund_seatedTicket_doesNotRestockInventory_doesNotNotifyWaitlist() throws Exception {
        User owner = persistUser("owner");
        User buyer = persistUser("buyer");
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID reservedTicketTypeId = persistReservedTicketType(eventId);
        persistRefundPolicy(eventId, RefundRuleType.CUSTOM);
        Order order = persistOrder(buyer.getId(), orgId, 2000L, OrderStatus.PAID);
        persistTicket(eventId, reservedTicketTypeId, buyer.getId(), order.getId(), UUID.randomUUID()); // seatId set -> reserved seating
        persistPayment(order.getId(), "mock_ref_" + UUID.randomUUID(), 2000L);
        String ownerToken = jwtService.generateAccessToken(owner);

        MvcResult refundResult = mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", order.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"seated ticket refund\"}"))
                .andReturn();
        trackAnyRefundsForOrder(order.getId());
        assertThat(refundResult.getResponse().getStatus()).isEqualTo(201);

        TicketType refreshedType = ticketTypeRepository.findById(reservedTicketTypeId).orElseThrow();
        assertThat(refreshedType.getQuantityAvailable()).isEqualTo(5); // untouched

        assertThat(notificationsFor(buyer.getId())).extracting(Notification::getType)
                .containsExactly(NotificationType.REFUND_CONFIRMATION); // REFUND_CONFIRMATION only, no WAITLIST_AVAILABILITY anywhere
    }

    /**
     * Two GA tickets of the SAME ticket type refunded in the same {@code
     * refundAllForEventCancellation} sweep, with two people waiting in
     * line: the FIRST-positioned waiter is notified from the first
     * ticket's restock, and the SECOND-positioned waiter from the second's
     * - not the same person notified twice, and not skipped.
     */
    @Test
    void cancelEvent_twoGaTicketsSameTypeRefunded_twoWaitersEachNotifiedFromTheirOwnRestock() throws Exception {
        User owner = persistUser("owner");
        User buyerA = persistUser("buyer-a");
        User buyerB = persistUser("buyer-b");
        User waiter1 = persistUser("waiter-1");
        User waiter2 = persistUser("waiter-2");
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId);
        UUID ticketTypeId = persistGaTicketType(eventId, 0, 2); // both units already sold, sold out

        String waiter1Token = jwtService.generateAccessToken(waiter1);
        mockMvc.perform(post("/api/v1/events/{eventId}/waitlist", eventId)
                        .header("Authorization", "Bearer " + waiter1Token)
                        .contentType("application/json")
                        .content("{\"ticketTypeId\":\"" + ticketTypeId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1));
        String waiter2Token = jwtService.generateAccessToken(waiter2);
        mockMvc.perform(post("/api/v1/events/{eventId}/waitlist", eventId)
                        .header("Authorization", "Bearer " + waiter2Token)
                        .contentType("application/json")
                        .content("{\"ticketTypeId\":\"" + ticketTypeId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(2));
        waitlistEntryRepository.findAll().stream()
                .filter(e -> e.getEventId().equals(eventId))
                .forEach(e -> createdWaitlistEntryIds.add(e.getId()));

        Order orderA = persistOrder(buyerA.getId(), orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyerA.getId(), orderA.getId(), null);
        persistPayment(orderA.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        Order orderB = persistOrder(buyerB.getId(), orgId, 1000L, OrderStatus.PAID);
        persistTicket(eventId, ticketTypeId, buyerB.getId(), orderB.getId(), null);
        persistPayment(orderB.getId(), "mock_ref_" + UUID.randomUUID(), 1000L);
        String ownerToken = jwtService.generateAccessToken(owner);

        // Cancelling the event refunds BOTH orders (refundAllForEventCancellation),
        // restocking the shared ticket type twice in the same sweep.
        MvcResult cancelResult = mockMvc.perform(post("/api/v1/events/{eventId}/cancel", eventId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andReturn();
        trackAnyRefundsForOrder(orderA.getId());
        trackAnyRefundsForOrder(orderB.getId());
        assertThat(cancelResult.getResponse().getStatus()).isEqualTo(202);

        TicketType refreshedType = ticketTypeRepository.findById(ticketTypeId).orElseThrow();
        assertThat(refreshedType.getQuantityAvailable()).isEqualTo(2);

        WaitlistEntry entry1 = waitlistEntryRepository.findByUserIdOrderByCreatedAtAsc(waiter1.getId()).get(0);
        WaitlistEntry entry2 = waitlistEntryRepository.findByUserIdOrderByCreatedAtAsc(waiter2.getId()).get(0);
        assertThat(entry1.getNotifiedAt()).as("first-positioned waiter must be notified").isNotNull();
        assertThat(entry2.getNotifiedAt()).as("second-positioned waiter must ALSO be notified (2 restocks)").isNotNull();

        // Each waiter notified exactly once - not double-notified, not skipped.
        assertThat(notificationsFor(waiter1.getId())).extracting(Notification::getType)
                .containsExactly(NotificationType.WAITLIST_AVAILABILITY);
        assertThat(notificationsFor(waiter2.getId())).extracting(Notification::getType)
                .containsExactly(NotificationType.WAITLIST_AVAILABILITY);
    }
}
