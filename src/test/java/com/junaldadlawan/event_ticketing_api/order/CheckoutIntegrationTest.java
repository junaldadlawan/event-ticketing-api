package com.junaldadlawan.event_ticketing_api.order;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.cart.entity.Cart;
import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.entity.CheckoutIdempotencyKey;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.entity.Payment;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
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
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatMapRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-DB verification of the three code-reviewer findings fixed alongside
 * this test (CRITICAL 1: concurrent checkout on the same cart with two
 * different idempotency keys; CRITICAL 2: {@code CheckoutIdempotencyKey}'s
 * {@code Persistable} fix; HIGH: promo-code usage-limit re-check at checkout
 * time). Mirrors {@code CartAccessIntegrationTest}'s real-Postgres,
 * real-concurrency style rather than mocking repositories.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutIntegrationTest {

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
    private CartRepository cartRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private PromoCodeRepository promoCodeRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private CheckoutIdempotencyKeyRepository checkoutIdempotencyKeyRepository;
    @Autowired
    private SeatMapRepository seatMapRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdCartItemIds = new ArrayList<>();
    private final List<UUID> createdCartIds = new ArrayList<>();
    private final List<UUID> createdPromoCodeIds = new ArrayList<>();
    private final List<UUID> createdTicketTypeIds = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<UUID> createdOrderIds = new ArrayList<>();
    private final List<UUID> createdPaymentIds = new ArrayList<>();
    private final List<UUID> createdIdempotencyKeyIds = new ArrayList<>();
    private final List<UUID> createdSeatIds = new ArrayList<>();
    private final List<UUID> createdSeatMapIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID id : createdPaymentIds) {
            paymentRepository.deleteById(id);
        }
        createdPaymentIds.clear();
        for (UUID id : createdOrderIds) {
            orderRepository.deleteById(id);
        }
        createdOrderIds.clear();
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
        for (UUID id : createdPromoCodeIds) {
            promoCodeRepository.deleteById(id);
        }
        createdPromoCodeIds.clear();
        for (UUID id : createdSeatIds) {
            seatRepository.deleteById(id);
        }
        createdSeatIds.clear();
        for (UUID id : createdSeatMapIds) {
            seatMapRepository.deleteById(id);
        }
        createdSeatMapIds.clear();
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
    }

    // ---- fixtures ----

    private User inMemoryUser(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local")
                .role(role)
                .build();
    }

    private UUID persistOrganization(UUID ownerId) {
        Organization organization = Organization.builder()
                .name("Checkout Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED)
                .ownerId(ownerId)
                .documents(List.of())
                .build();
        Organization saved = organizationRepository.save(organization);
        createdOrgIds.add(saved.getId());
        return saved.getId();
    }

    private void grantOrgRole(UUID userId, UUID organizationId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder()
                .userId(userId)
                .organizationId(organizationId)
                .build();
        member.getRoles().add(role);
        createdMembers.add(organizationMemberRepository.save(member));
    }

    private UUID persistEvent(UUID organizationId, EventStatus status) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = Event.builder()
                .organizationId(organizationId)
                .title("Checkout Test Event")
                .description("desc")
                .category("music")
                .status(status)
                .ticketPrefix("K" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistGaTicketType(UUID eventId, int quantityAvailable) {
        TicketType tt = TicketType.builder()
                .eventId(eventId)
                .name("GA")
                .kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency("USD").build())
                .quantityTotal(quantityAvailable)
                .quantityAvailable(quantityAvailable)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10)
                .build();
        TicketType saved = ticketTypeRepository.save(tt);
        createdTicketTypeIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistReservedTicketType(UUID eventId, long priceAmount) {
        TicketType tt = TicketType.builder()
                .eventId(eventId)
                .name("Reserved")
                .kind(TicketTypeKind.RESERVED_SEATING)
                .price(Money.builder().amount(priceAmount).currency("USD").build())
                .quantityTotal(10)
                .quantityAvailable(10)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10)
                .build();
        TicketType saved = ticketTypeRepository.save(tt);
        createdTicketTypeIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistSeatMap(UUID eventId) {
        SeatMap seatMap = SeatMap.builder().eventId(eventId).build();
        SeatMap saved = seatMapRepository.save(seatMap);
        createdSeatMapIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistAvailableSeat(UUID seatMapId) {
        Seat seat = Seat.builder()
                .seatMapId(seatMapId)
                .section("A")
                .row("1")
                .seatNumber("1")
                .status(SeatStatus.AVAILABLE)
                .build();
        Seat saved = seatRepository.save(seat);
        createdSeatIds.add(saved.getId());
        return saved.getId();
    }

    private UUID persistPromoCode(UUID eventId, Integer usageLimitTotal) {
        return persistPromoCode(eventId, "LIMIT1", usageLimitTotal, DiscountType.FIXED, BigDecimal.valueOf(100));
    }

    private UUID persistPromoCode(UUID eventId, String code, Integer usageLimitTotal,
                                   DiscountType discountType, BigDecimal discountValue) {
        PromoCode promoCode = PromoCode.builder()
                .eventId(eventId)
                .code(code)
                .discountType(discountType)
                .discountValue(discountValue)
                .applicableTicketTypeIds(Set.of())
                .usageLimitTotal(usageLimitTotal)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validUntil(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        PromoCode saved = promoCodeRepository.save(promoCode);
        createdPromoCodeIds.add(saved.getId());
        return saved.getId();
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
        var items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        for (var item : items) {
            createdCartItemIds.add(UUID.fromString(item.get("id").asText()));
        }
    }

    private void addReservedSeatItem(String token, UUID cartId, UUID ticketTypeId, UUID seatId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/carts/{cartId}/items", cartId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"seatId\":\"" + seatId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        var items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        for (var item : items) {
            createdCartItemIds.add(UUID.fromString(item.get("id").asText()));
        }
    }

    private MvcResult checkout(String token, UUID cartId, UUID idempotencyKey, String paymentMethodToken) throws Exception {
        return mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"" + paymentMethodToken + "\"}"))
                .andReturn();
    }

    private void applyPromoCode(String token, UUID cartId, String code) throws Exception {
        mockMvc.perform(post("/api/v1/carts/{cartId}/promo-code", cartId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());
    }

    private void trackOrderAndPaymentFromResponse(MvcResult result) throws Exception {
        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        var idNode = json.get("id");
        if (idNode == null) {
            return;
        }
        UUID orderId = UUID.fromString(idNode.asText());
        createdOrderIds.add(orderId);
        for (Payment payment : paymentRepository.findByOrderId(orderId)) {
            createdPaymentIds.add(payment.getId());
        }
    }

    // ---- CRITICAL 1: two different idempotency keys, same cart, concurrently ----

    @Test
    void concurrency_sameCartDifferentIdempotencyKeys_exactlyOneOrderAndOnePayment() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeId = persistGaTicketType(eventId, 5);
        String buyerToken = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(buyerToken);
        addGaItem(buyerToken, cartId, ticketTypeId);

        UUID keyA = UUID.randomUUID();
        UUID keyB = UUID.randomUUID();
        createdIdempotencyKeyIds.add(keyA);
        createdIdempotencyKeyIds.add(keyB);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> requestA = () -> {
                startLatch.await();
                return mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                                .header("Authorization", "Bearer " + buyerToken)
                                .header("Idempotency-Key", keyA.toString())
                                .contentType("application/json")
                                .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> requestB = () -> {
                startLatch.await();
                return mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                                .header("Authorization", "Bearer " + buyerToken)
                                .header("Idempotency-Key", keyB.toString())
                                .contentType("application/json")
                                .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                        .andReturn().getResponse().getStatus();
            };

            Future<Integer> futureA = pool.submit(requestA);
            Future<Integer> futureB = pool.submit(requestB);
            startLatch.countDown();

            int statusA = futureA.get(15, TimeUnit.SECONDS);
            int statusB = futureB.get(15, TimeUnit.SECONDS);

            // Exactly one succeeds (201); the other must NOT also succeed.
            // The loser's exact status depends on scheduling (409 empty-cart
            // if it loses the row-lock race after the winner's CartItems are
            // already deleted), but it must never be 201.
            List<Integer> statuses = List.of(statusA, statusB);
            long successCount = statuses.stream().filter(s -> s == 201).count();
            assertThat(successCount)
                    .as("exactly one of the two concurrent checkout attempts should succeed, got statuses %s", statuses)
                    .isEqualTo(1);
            assertThat(statuses).allMatch(s -> s == 201 || s == 409,
                    "unexpected status code(s) in " + statuses);
        } finally {
            pool.shutdown();
        }

        List<Order> orders = orderRepository.findByCartId(cartId);
        for (Order order : orders) {
            createdOrderIds.add(order.getId());
        }
        assertThat(orders).as("exactly one Order row for this cart").hasSize(1);

        List<Payment> payments = paymentRepository.findByOrderId(orders.get(0).getId());
        for (Payment payment : payments) {
            createdPaymentIds.add(payment.getId());
        }
        assertThat(payments).as("exactly one Payment (one gateway charge) for the single Order").hasSize(1);

        // Track any surviving CartItem for cleanup (should be none - the
        // winning checkout deletes them).
        for (CartItem item : cartItemRepository.findByCartId(cartId)) {
            createdCartItemIds.add(item.getId());
        }
    }

    // ---- CRITICAL 2: CheckoutIdempotencyKey now routes a fresh claim through persist(), not merge() ----

    @Test
    void checkoutIdempotencyKey_duplicateIdOnFreshInstance_throwsConstraintViolationInsteadOfSilentlyMerging() {
        UUID keyId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        createdIdempotencyKeyIds.add(keyId);

        CheckoutIdempotencyKey first = CheckoutIdempotencyKey.builder()
                .id(keyId)
                .buyerId(buyerId)
                .cartId(cartId)
                .build();
        checkoutIdempotencyKeyRepository.saveAndFlush(first);

        // A second, entirely separate (fresh - isNew defaults to true)
        // instance with the SAME id, as would happen if CheckoutIdempotencyKeyManager.claim()'s
        // "lost the race" DataIntegrityViolationException path were bypassed - this
        // proves save() now actually attempts an INSERT (persist()) rather than
        // silently UPDATE-ing the existing row via merge() (code-reviewer CRITICAL 2).
        CheckoutIdempotencyKey second = CheckoutIdempotencyKey.builder()
                .id(keyId)
                .buyerId(UUID.randomUUID()) // different buyer - would silently overwrite under the old merge() bug
                .cartId(UUID.randomUUID())
                .build();

        assertThatThrownBy(() -> checkoutIdempotencyKeyRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Confirm the original row was NOT overwritten by the failed second attempt.
        CheckoutIdempotencyKey persisted = checkoutIdempotencyKeyRepository.findById(keyId).orElseThrow();
        assertThat(persisted.getBuyerId()).isEqualTo(buyerId);
    }

    // ---- HIGH: promo-code usage-limit re-checked at checkout, not just at applyPromoCode time ----

    @Test
    void checkout_promoCodeUsageLimitExhaustedByAnotherBuyerSinceApply_returns422() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyerA = inMemoryUser(Role.CUSTOMER);
        User buyerB = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeId = persistGaTicketType(eventId, 5);
        persistPromoCode(eventId, 1); // usageLimitTotal = 1

        String tokenA = jwtService.generateAccessToken(buyerA);
        String tokenB = jwtService.generateAccessToken(buyerB);
        UUID cartA = createCart(tokenA);
        UUID cartB = createCart(tokenB);
        addGaItem(tokenA, cartA, ticketTypeId);
        addGaItem(tokenB, cartB, ticketTypeId);

        // Both applications succeed - no PAID order referencing the code exists yet.
        applyPromoCode(tokenA, cartA, "LIMIT1");
        applyPromoCode(tokenB, cartB, "LIMIT1");

        // Buyer A checks out first - consumes the code's only unit of usage.
        UUID keyA = UUID.randomUUID();
        createdIdempotencyKeyIds.add(keyA);
        MvcResult resultA = mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartA)
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", keyA.toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        trackOrderAndPaymentFromResponse(resultA);

        UUID keyB = UUID.randomUUID();
        createdIdempotencyKeyIds.add(keyB);
        // Buyer B's checkout must now be rejected even though their earlier
        // applyPromoCode() call succeeded (BR-PROMO-006 re-check at checkout).
        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartB)
                        .header("Authorization", "Bearer " + tokenB)
                        .header("Idempotency-Key", keyB.toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andExpect(status().isUnprocessableEntity());

        // Buyer B's cart/items/hold must remain fully intact after the 422 -
        // and the idempotency key must have been freed for retry (BR-NFR-008).
        assertThat(cartItemRepository.findByCartId(cartB)).hasSize(1);
        for (CartItem item : cartItemRepository.findByCartId(cartB)) {
            createdCartItemIds.add(item.getId());
        }
        assertThat(checkoutIdempotencyKeyRepository.findById(keyB)).isEmpty();
    }

    // ---- empty cart -> 409 (BR-CART-002/003 - no Order without items) ----

    @Test
    void checkout_emptyCart_returns409_andNoOrderCreated() throws Exception {
        User buyer = inMemoryUser(Role.CUSTOMER);
        String token = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(token);

        UUID key = UUID.randomUUID();
        createdIdempotencyKeyIds.add(key);
        MvcResult result = checkout(token, cartId, key, "tok_ok");
        assertThat(result.getResponse().getStatus()).isEqualTo(409);

        assertThat(orderRepository.findByCartId(cartId)).isEmpty();
        // Frees the key for retry (BR-NFR-008 generalized to any failure path).
        assertThat(checkoutIdempotencyKeyRepository.findById(key)).isEmpty();
    }

    // ---- expired hold -> 410, and the expired hold is released as a side effect ----

    @Test
    void checkout_expiredHold_returns410_andReleasesHoldEvenThoughRequestFails() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeId = persistGaTicketType(eventId, 5);
        String token = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(token);
        addGaItem(token, cartId, ticketTypeId);

        // Directly age the just-placed hold into the past, simulating the
        // 15-minute hold window having elapsed before checkout runs.
        List<CartItem> items = cartItemRepository.findByCartId(cartId);
        assertThat(items).hasSize(1);
        CartItem item = items.get(0);
        item.setHoldExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        cartItemRepository.save(item);

        UUID key = UUID.randomUUID();
        createdIdempotencyKeyIds.add(key);
        MvcResult result = checkout(token, cartId, key, "tok_ok");
        assertThat(result.getResponse().getStatus()).isEqualTo(410);

        assertThat(orderRepository.findByCartId(cartId)).isEmpty();
        assertThat(checkoutIdempotencyKeyRepository.findById(key)).isEmpty();
        // Side effect: the expired hold was released as part of failing the
        // request - the CartItem is gone and the TicketType's quantityAvailable
        // was restored (started at 5, one held for qty 1, now back to 5).
        assertThat(cartItemRepository.findByCartId(cartId)).isEmpty();
        TicketType refreshed = ticketTypeRepository.findById(ticketTypeId).orElseThrow();
        assertThat(refreshed.getQuantityAvailable()).isEqualTo(5);
    }

    // ---- payment failure (402) then retry with a new idempotency key succeeds ----

    @Test
    void checkout_paymentFailure_thenRetryWithNewIdempotencyKey_succeeds() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeId = persistGaTicketType(eventId, 3);
        String token = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(token);
        addGaItem(token, cartId, ticketTypeId);

        UUID failKey = UUID.randomUUID();
        createdIdempotencyKeyIds.add(failKey);
        MvcResult failResult = checkout(token, cartId, failKey, "tok_fail");
        assertThat(failResult.getResponse().getStatus()).isEqualTo(402);

        // Cart/items/holds fully intact, no Order/Payment, key freed.
        assertThat(orderRepository.findByCartId(cartId)).isEmpty();
        assertThat(cartItemRepository.findByCartId(cartId)).hasSize(1);
        assertThat(checkoutIdempotencyKeyRepository.findById(failKey)).isEmpty();

        UUID retryKey = UUID.randomUUID();
        createdIdempotencyKeyIds.add(retryKey);
        MvcResult retryResult = checkout(token, cartId, retryKey, "tok_ok");
        assertThat(retryResult.getResponse().getStatus()).isEqualTo(201);
        trackOrderAndPaymentFromResponse(retryResult);

        List<Order> orders = orderRepository.findByCartId(cartId);
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(cartItemRepository.findByCartId(cartId)).isEmpty();
    }

    // ---- idempotent replay: same key after a successful checkout returns the same Order ----

    @Test
    void checkout_replaySameIdempotencyKeyAfterSuccess_returnsSameOrder_noRecharge() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeId = persistGaTicketType(eventId, 3);
        String token = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(token);
        addGaItem(token, cartId, ticketTypeId);

        UUID key = UUID.randomUUID();
        createdIdempotencyKeyIds.add(key);
        MvcResult firstResult = checkout(token, cartId, key, "tok_ok");
        assertThat(firstResult.getResponse().getStatus()).isEqualTo(201);
        trackOrderAndPaymentFromResponse(firstResult);
        UUID firstOrderId = UUID.fromString(objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("id").asText());

        MvcResult replayResult = checkout(token, cartId, key, "tok_ok");
        assertThat(replayResult.getResponse().getStatus()).isEqualTo(201);
        UUID replayOrderId = UUID.fromString(objectMapper.readTree(replayResult.getResponse().getContentAsString()).get("id").asText());

        assertThat(replayOrderId).isEqualTo(firstOrderId);
        assertThat(orderRepository.findByCartId(cartId)).hasSize(1);
        assertThat(paymentRepository.findByOrderId(firstOrderId)).hasSize(1);
    }

    // ---- cross-buyer reuse of a completed idempotency key -> 403 ----

    @Test
    void checkout_crossBuyerIdempotencyKeyReuse_returns403() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyerA = inMemoryUser(Role.CUSTOMER);
        User buyerB = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);
        UUID ticketTypeId = persistGaTicketType(eventId, 5);
        String tokenA = jwtService.generateAccessToken(buyerA);
        String tokenB = jwtService.generateAccessToken(buyerB);
        UUID cartA = createCart(tokenA);
        UUID cartB = createCart(tokenB);
        addGaItem(tokenA, cartA, ticketTypeId);
        addGaItem(tokenB, cartB, ticketTypeId);

        UUID sharedKey = UUID.randomUUID();
        createdIdempotencyKeyIds.add(sharedKey);
        MvcResult resultA = checkout(tokenA, cartA, sharedKey, "tok_ok");
        assertThat(resultA.getResponse().getStatus()).isEqualTo(201);
        trackOrderAndPaymentFromResponse(resultA);

        // Buyer B attempts to reuse buyer A's already-claimed idempotency key
        // against their own cart.
        MvcResult resultB = checkout(tokenB, cartB, sharedKey, "tok_ok");
        assertThat(resultB.getResponse().getStatus()).isEqualTo(403);

        // Buyer B's cart/hold must remain untouched by the rejected attempt.
        assertThat(cartItemRepository.findByCartId(cartB)).hasSize(1);
    }

    // ---- capstone: GA + reserved-seating items + a promo code, in one cart, in one checkout ----

    @Test
    void checkout_success_gaAndReservedSeatingItemsWithPromoCode_composesPhase5aAndPhase5bCorrectly() throws Exception {
        User owner = inMemoryUser(Role.CUSTOMER);
        User buyer = inMemoryUser(Role.CUSTOMER);
        UUID orgId = persistOrganization(owner.getId());
        grantOrgRole(owner.getId(), orgId, OrganizationRole.OWNER);
        UUID eventId = persistEvent(orgId, EventStatus.PUBLISHED);

        UUID gaTicketTypeId = persistGaTicketType(eventId, 5); // price 1000, qty 5
        UUID reservedTicketTypeId = persistReservedTicketType(eventId, 2000L);
        UUID seatMapId = persistSeatMap(eventId);
        UUID seatId = persistAvailableSeat(seatMapId);
        UUID promoCodeId = persistPromoCode(eventId, "CAPSTONE10", null, DiscountType.FIXED, BigDecimal.valueOf(300));

        String token = jwtService.generateAccessToken(buyer);
        UUID cartId = createCart(token);
        addGaItem(token, cartId, gaTicketTypeId); // subtotal += 1000
        addReservedSeatItem(token, cartId, reservedTicketTypeId, seatId); // subtotal += 2000
        applyPromoCode(token, cartId, "CAPSTONE10"); // subtotal 3000 - 300 = 2700

        UUID key = UUID.randomUUID();
        createdIdempotencyKeyIds.add(key);
        MvcResult result = checkout(token, cartId, key, "tok_ok");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        trackOrderAndPaymentFromResponse(result);

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asText()).isEqualTo("PAID");
        assertThat(json.get("payeeType").asText()).isEqualTo("ORGANIZATION");
        assertThat(json.get("payeeId").asText()).isEqualTo(orgId.toString());
        assertThat(json.get("total").get("amount").asLong()).isEqualTo(2700L);
        assertThat(json.get("tickets").isArray()).isTrue();
        assertThat(json.get("tickets").isEmpty()).isTrue();
        UUID orderId = UUID.fromString(json.get("id").asText());

        // Order/Payment final state.
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getCartId()).isEqualTo(cartId);
        assertThat(order.getPromoCode()).isEqualTo("CAPSTONE10");
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payments.get(0).getAmount().getAmount()).isEqualTo(2700L);

        // GA TicketType.quantityAvailable stays at its hold-time-decremented
        // value (5 - 1 = 4); checkout does not touch it further.
        TicketType gaTicketType = ticketTypeRepository.findById(gaTicketTypeId).orElseThrow();
        assertThat(gaTicketType.getQuantityAvailable()).isEqualTo(4);

        // Reserved seat flips HELD -> SOLD.
        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.SOLD);

        // Both CartItems deleted; cart's promo code cleared.
        assertThat(cartItemRepository.findByCartId(cartId)).isEmpty();
        Cart cart = cartRepository.findById(cartId).orElseThrow();
        assertThat(cart.getPromoCodeId()).isNull();
    }
}
