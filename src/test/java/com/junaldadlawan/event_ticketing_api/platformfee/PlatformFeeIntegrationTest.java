package com.junaldadlawan.event_ticketing_api.platformfee;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.entity.CheckoutIdempotencyKey;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.repository.CheckoutIdempotencyKeyRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PaymentRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.payout.entity.Payout;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutRepository;
import com.junaldadlawan.event_ticketing_api.platformfee.entity.PlatformFeeRule;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeScope;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeType;
import com.junaldadlawan.event_ticketing_api.platformfee.repository.PlatformFeeRuleRepository;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end for the platform fee ("admin cut"): the admin sets a percentage or flat rate for the platform, an
 * organization or an event (the most specific wins); the cart shows it, checkout charges ticket total + fee and
 * stores it on the order; and the payout an admin generates deducts it from what the organizer is owed.
 * The platform default is a single global row, so the test sets any existing one aside and puts it back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlatformFeeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformFeeRuleRepository ruleRepository;
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
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private CheckoutIdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired
    private PayoutRepository payoutRepository;
    @Autowired
    private PromoCodeRepository promoCodes;

    private final List<UUID> promoIds = new ArrayList<>();
    private final List<UUID> orgIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<UUID> ticketTypeIds = new ArrayList<>();
    private final List<UUID> cartIds = new ArrayList<>();
    private final List<UUID> cartItemIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> idempotencyKeyIds = new ArrayList<>();
    private final List<UUID> payoutIds = new ArrayList<>();
    private UUID setAsideDefault;
    private Instant startedAt;

    private String admin;
    private String customer;
    private UUID buyerId;

    @BeforeEach
    void setUp() {
        startedAt = Instant.now().minusSeconds(1);
        PlatformFeeRule existing = ruleRepository.findByScopeAndScopeIdIsNullAndDeletedAtIsNull(FeeScope.PLATFORM).orElse(null);
        if (existing != null) {
            setAsideDefault = existing.getId();
            jdbc.update("update platform_fee_rules set deleted_at = now() where id = ?", setAsideDefault);
        }
        admin = token(User.builder().id(UUID.randomUUID()).email("fee-admin-" + UUID.randomUUID() + "@test.local").role(Role.ADMIN).build());
        buyerId = UUID.randomUUID();
        customer = token(User.builder().id(buyerId).email("fee-buyer-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build());
    }

    @AfterEach
    void tearDown() {
        for (UUID id : orderIds) {
            ticketRepository.findByOrderId(id).forEach(ticket -> ticketRepository.deleteById(ticket.getId()));
            paymentRepository.findByOrderId(id).forEach(payment -> paymentRepository.deleteById(payment.getId()));
            orderRepository.deleteById(id);
        }
        payoutIds.forEach(payoutRepository::deleteById);
        idempotencyKeyIds.forEach(idempotencyKeyRepository::deleteById);
        cartItemIds.forEach(cartItemRepository::deleteById);
        cartIds.forEach(cartRepository::deleteById);
        ticketTypeIds.forEach(ticketTypeRepository::deleteById);
        eventIds.forEach(eventRepository::deleteById);
        members.forEach(member -> organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        orgIds.forEach(organizationRepository::deleteById);
        // every rule this test made (the default too); the one that was set aside is older and is left alone
        jdbc.update("delete from platform_fee_rules where created_at >= ?", java.sql.Timestamp.from(startedAt));
        promoIds.forEach(promoCodes::deleteById);
        if (setAsideDefault != null) {
            jdbc.update("update platform_fee_rules set deleted_at = null where id = ?", setAsideDefault);
        }
    }

    // ---- fixtures ----

    private String token(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private UUID org() {
        Organization saved = organizationRepository.save(Organization.builder().name("Fee Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(UUID.randomUUID()).documents(List.of()).build());
        orgIds.add(saved.getId());
        return saved.getId();
    }

    private UUID event(UUID orgId) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Event saved = eventRepository.save(Event.builder().organizationId(orgId).title("Fee Test Event").description("d").category("music")
                .status(EventStatus.ON_SALE).ticketPrefix("F" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC").build());
        eventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID ticketType(UUID eventId, long price) {
        TicketType saved = ticketTypeRepository.save(TicketType.builder().eventId(eventId).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(price).currency("USD").build()).quantityTotal(50).quantityAvailable(50)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS)).maxPerOrder(10).build());
        ticketTypeIds.add(saved.getId());
        return saved.getId();
    }

    private void putRule(String path, String body, int expected) throws Exception {
        mockMvc.perform(put(path).header("Authorization", admin).contentType("application/json").content(body)).andExpect(status().is(expected));
    }

    private void percentage(String path, String percent) throws Exception {
        putRule(path, "{\"type\":\"PERCENTAGE\",\"percentage\":" + percent + "}", 200);
    }

    private void flat(String path, long amount) throws Exception {
        putRule(path, "{\"type\":\"FLAT\",\"flatAmount\":{\"amount\":" + amount + ",\"currency\":\"USD\"}}", 200);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID cartWith(UUID ticketTypeId, int quantity) throws Exception {
        UUID cartId = UUID.fromString(body(mockMvc.perform(post("/api/v1/carts").header("Authorization", customer))
                .andExpect(status().isCreated()).andReturn()).get("id").asText());
        cartIds.add(cartId);
        MvcResult added = mockMvc.perform(post("/api/v1/carts/{id}/items", cartId).header("Authorization", customer)
                        .contentType("application/json")
                        .content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated()).andReturn();
        body(added).get("items").forEach(item -> cartItemIds.add(UUID.fromString(item.get("id").asText())));
        return cartId;
    }

    private JsonNode cart(UUID cartId) throws Exception {
        return body(mockMvc.perform(get("/api/v1/carts/{id}", cartId).header("Authorization", customer))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode checkout(UUID cartId) throws Exception {
        UUID key = UUID.randomUUID();
        idempotencyKeyIds.add(key);
        MvcResult result = mockMvc.perform(post("/api/v1/carts/{id}/checkout", cartId).header("Authorization", customer)
                        .header("Idempotency-Key", key.toString()).contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode order = body(result);
        orderIds.add(UUID.fromString(order.get("id").asText()));
        return order;
    }

    // ---- who may manage the rules ----

    @Test
    void theRulesAreAdminOnly() throws Exception {
        UUID orgId = org();
        UUID eventId = event(orgId);
        String rule = "{\"type\":\"PERCENTAGE\",\"percentage\":5}";

        for (String path : new String[] {"/default", "/organizations/" + orgId, "/events/" + eventId}) {
            mockMvc.perform(put("/api/v1/platform-fees" + path).header("Authorization", customer).contentType("application/json").content(rule))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/platform-fees" + path).contentType("application/json").content(rule)).andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/v1/platform-fees" + path).header("Authorization", customer)).andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/platform-fees" + path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/v1/platform-fees").header("Authorization", customer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/platform-fees")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/platform-fees/effective").param("eventId", eventId.toString()).header("Authorization", customer))
                .andExpect(status().isForbidden());
        assertThat(ruleRepository.findByScopeAndScopeIdIsNullAndDeletedAtIsNull(FeeScope.PLATFORM)).isEmpty();
    }

    @Test
    void anAdminSetsReplacesListsAndRemovesRules() throws Exception {
        UUID orgId = org();

        percentage("/api/v1/platform-fees/default", "10");
        flat("/api/v1/platform-fees/organizations/" + orgId, 300);
        mockMvc.perform(get("/api/v1/platform-fees").param("scope", "ORGANIZATION").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.scopeId=='" + orgId + "')].flatAmount.amount").value(300));

        // a second PUT replaces the rule of that scope instead of adding another
        percentage("/api/v1/platform-fees/organizations/" + orgId, "2.5");
        MvcResult list = mockMvc.perform(get("/api/v1/platform-fees").header("Authorization", admin)).andExpect(status().isOk()).andReturn();
        long forOrg = 0;
        for (JsonNode rule : body(list)) {
            if (rule.hasNonNull("scopeId") && rule.get("scopeId").asText().equals(orgId.toString())) {
                forOrg++;
                assertThat(rule.get("type").asText()).isEqualTo("PERCENTAGE");
                assertThat(rule.get("percentage").asDouble()).isEqualTo(2.5);
                assertThat(rule.get("flatAmount").isNull()).isTrue();
            }
        }
        assertThat(forOrg).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/platform-fees/organizations/{id}", orgId).header("Authorization", admin)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/platform-fees/organizations/{id}", orgId).header("Authorization", admin)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/platform-fees/default").header("Authorization", admin)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/platform-fees/default").header("Authorization", admin)).andExpect(status().isNotFound());
    }

    @Test
    void invalidRulesAndUnknownTargetsAreRefused() throws Exception {
        putRule("/api/v1/platform-fees/default", "{\"type\":\"PERCENTAGE\"}", 400);
        putRule("/api/v1/platform-fees/default", "{\"type\":\"PERCENTAGE\",\"percentage\":101}", 400);
        putRule("/api/v1/platform-fees/default", "{\"type\":\"PERCENTAGE\",\"percentage\":-1}", 400);
        putRule("/api/v1/platform-fees/default", "{\"type\":\"FLAT\"}", 400);
        putRule("/api/v1/platform-fees/default", "{\"type\":\"FLAT\",\"percentage\":5,\"flatAmount\":{\"amount\":5,\"currency\":\"USD\"}}", 400);
        putRule("/api/v1/platform-fees/organizations/" + UUID.randomUUID(), "{\"type\":\"PERCENTAGE\",\"percentage\":5}", 404);
        putRule("/api/v1/platform-fees/events/" + UUID.randomUUID(), "{\"type\":\"PERCENTAGE\",\"percentage\":5}", 404);
        assertThat(ruleRepository.findByScopeAndScopeIdIsNullAndDeletedAtIsNull(FeeScope.PLATFORM)).isEmpty();
    }

    // ---- the cart shows the fee, the most specific rule wins ----

    @Test
    void theCartAddsTheFeeOnTopOfTheTicketPrice_withTheMostSpecificRuleWinning() throws Exception {
        UUID orgId = org();
        UUID eventId = event(orgId);
        UUID cartId = cartWith(ticketType(eventId, 1000), 2);   // tickets: 2000

        // no rule at all: no fee
        JsonNode noRule = cart(cartId);
        assertThat(noRule.get("platformFee").get("amount").asLong()).isZero();
        assertThat(noRule.get("total").get("amount").asLong()).isEqualTo(2000L);

        percentage("/api/v1/platform-fees/default", "10");
        JsonNode platform = cart(cartId);
        assertThat(platform.get("platformFee").get("amount").asLong()).isEqualTo(200L);
        assertThat(platform.get("total").get("amount").asLong()).isEqualTo(2200L);
        assertThat(platform.get("platformFee").get("currency").asText()).isEqualTo("USD");

        flat("/api/v1/platform-fees/organizations/" + orgId, 50);
        assertThat(cart(cartId).get("platformFee").get("amount").asLong()).isEqualTo(50L);
        assertThat(cart(cartId).get("total").get("amount").asLong()).isEqualTo(2050L);

        percentage("/api/v1/platform-fees/events/" + eventId, "0");   // a waiver for this event
        assertThat(cart(cartId).get("platformFee").get("amount").asLong()).isZero();
        assertThat(cart(cartId).get("total").get("amount").asLong()).isEqualTo(2000L);

        mockMvc.perform(delete("/api/v1/platform-fees/events/{id}", eventId).header("Authorization", admin)).andExpect(status().isNoContent());
        assertThat(cart(cartId).get("platformFee").get("amount").asLong()).isEqualTo(50L);
        mockMvc.perform(delete("/api/v1/platform-fees/organizations/{id}", orgId).header("Authorization", admin)).andExpect(status().isNoContent());
        assertThat(cart(cartId).get("platformFee").get("amount").asLong()).isEqualTo(200L);

        mockMvc.perform(get("/api/v1/platform-fees/effective").param("eventId", eventId.toString()).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(orgId.toString()))
                .andExpect(jsonPath("$.rule.scope").value("PLATFORM"));
    }

    @Test
    void anotherOrganizationsOverrideNeverAffectsThisEvent() throws Exception {
        UUID orgId = org();
        UUID otherOrg = org();
        UUID cartId = cartWith(ticketType(event(orgId), 1000), 1);
        percentage("/api/v1/platform-fees/default", "10");
        flat("/api/v1/platform-fees/organizations/" + otherOrg, 999);

        assertThat(cart(cartId).get("platformFee").get("amount").asLong()).isEqualTo(100L);
    }

    // ---- checkout charges ticket total + fee and stores it ----

    @Test
    void checkoutChargesTheTicketTotalPlusTheFee_andTheOrderKeepsItAndTheRuleSnapshot() throws Exception {
        UUID orgId = org();
        UUID eventId = event(orgId);
        percentage("/api/v1/platform-fees/default", "10");
        UUID cartId = cartWith(ticketType(eventId, 1000), 1);

        JsonNode order = checkout(cartId);

        assertThat(order.get("platformFee").get("amount").asLong()).isEqualTo(100L);
        assertThat(order.get("total").get("amount").asLong()).isEqualTo(1100L);
        UUID orderId = UUID.fromString(order.get("id").asText());
        Order stored = orderRepository.findById(orderId).orElseThrow();
        assertThat(stored.getTotal().getAmount()).isEqualTo(1100L);
        assertThat(stored.getPlatformFeeAmount()).isEqualTo(100L);
        assertThat(stored.getPlatformFeeScope()).isEqualTo(FeeScope.PLATFORM);
        assertThat(stored.getPlatformFeeType()).isEqualTo(FeeType.PERCENTAGE);
        assertThat(stored.getPlatformFeePercentage()).isEqualByComparingTo("10");
        assertThat(paymentRepository.findByOrderId(orderId)).singleElement()
                .satisfies(payment -> assertThat(payment.getAmount().getAmount()).isEqualTo(1100L));

        // changing the rate later never rewrites an order that was already placed
        percentage("/api/v1/platform-fees/default", "50");
        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformFee.amount").value(100))
                .andExpect(jsonPath("$.total.amount").value(1100));
    }

    @Test
    void theFeeIsWorkedOutOnTheTotalAfterAPromoDiscount() throws Exception {
        UUID orgId = org();
        UUID eventId = event(orgId);
        percentage("/api/v1/platform-fees/default", "10");
        UUID cartId = cartWith(ticketType(eventId, 1000), 2);   // tickets 2000
        com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode promo = promoCodes.save(
                com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode.builder().eventId(eventId).code("FEE50")
                        .discountType(com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType.FIXED)
                        .discountValue(java.math.BigDecimal.valueOf(500)).applicableTicketTypeIds(java.util.Set.of())
                        .validFrom(Instant.now().minus(1, ChronoUnit.DAYS)).validUntil(Instant.now().plus(1, ChronoUnit.DAYS)).build());
        promoIds.add(promo.getId());
        mockMvc.perform(post("/api/v1/carts/{id}/promo-code", cartId).header("Authorization", customer)
                        .contentType("application/json").content("{\"code\":\"FEE50\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformFee.amount").value(150))   // 10% of 1500
                .andExpect(jsonPath("$.total.amount").value(1650));

        JsonNode order = checkout(cartId);

        assertThat(order.get("platformFee").get("amount").asLong()).isEqualTo(150L);
        assertThat(order.get("total").get("amount").asLong()).isEqualTo(1650L);
    }

    @Test
    void aFlatFeeIsPerOrder_notPerTicket() throws Exception {
        UUID orgId = org();
        flat("/api/v1/platform-fees/organizations/" + orgId, 75);
        UUID cartId = cartWith(ticketType(event(orgId), 1000), 4);

        JsonNode order = checkout(cartId);

        assertThat(order.get("platformFee").get("amount").asLong()).isEqualTo(75L);
        assertThat(order.get("total").get("amount").asLong()).isEqualTo(4075L);
    }

    @Test
    void withNoRule_checkoutChargesTheTicketTotalOnly() throws Exception {
        UUID cartId = cartWith(ticketType(event(org()), 1000), 1);

        JsonNode order = checkout(cartId);

        assertThat(order.get("platformFee").get("amount").asLong()).isZero();
        assertThat(order.get("total").get("amount").asLong()).isEqualTo(1000L);
        Order stored = orderRepository.findById(UUID.fromString(order.get("id").asText())).orElseThrow();
        assertThat(stored.getPlatformFeeScope()).isNull();
    }

    // ---- the organizer sees ticket revenue, not the fee ----

    @Test
    void anEventsRevenueLeavesOutThePlatformFee() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID orgId = org();
        OrganizationMember member = OrganizationMember.builder().userId(ownerId).organizationId(orgId).build();
        member.getRoles().add(OrganizationRole.OWNER);
        members.add(organizationMemberRepository.save(member));
        UUID eventId = event(orgId);
        percentage("/api/v1/platform-fees/default", "10");
        checkout(cartWith(ticketType(eventId, 1000), 2));   // buyer pays 2200: 2000 tickets + 200 fee

        mockMvc.perform(get("/api/v1/events/{id}/analytics", eventId)
                        .header("Authorization", token(User.builder().id(ownerId).email("fee-owner@test.local").role(Role.CUSTOMER).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue.amount").value(2000));
    }

    // ---- the payout deducts the fee ----

    private JsonNode generatePayout(UUID orgId, String authorization, int expected) throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        MvcResult result = mockMvc.perform(post("/api/v1/organizations/{id}/payouts", orgId).header("Authorization", authorization)
                        .contentType("application/json")
                        .content("{\"periodStart\":\"" + today.minusDays(1) + "\",\"periodEnd\":\"" + today + "\"}"))
                .andExpect(status().is(expected)).andReturn();
        JsonNode json = body(result);
        if (expected == 201) {
            payoutIds.add(UUID.fromString(json.get("id").asText()));
        }
        return json;
    }

    @Test
    void generatingAPayout_deductsThePlatformFees_andSettlesTheOrdersOnce() throws Exception {
        UUID orgId = org();
        UUID eventId = event(orgId);
        percentage("/api/v1/platform-fees/default", "10");
        UUID tt = ticketType(eventId, 1000);
        JsonNode first = checkout(cartWith(tt, 1));   // 1100 paid, fee 100
        JsonNode second = checkout(cartWith(tt, 2));  // 2200 paid, fee 200

        JsonNode payout = generatePayout(orgId, admin, 201);

        assertThat(payout.get("gross").get("amount").asLong()).isEqualTo(3300L);
        assertThat(payout.get("fees").get("amount").asLong()).isEqualTo(300L);
        assertThat(payout.get("net").get("amount").asLong()).isEqualTo(3000L);
        assertThat(payout.get("status").asText()).isEqualTo("SCHEDULED");
        UUID payoutId = UUID.fromString(payout.get("id").asText());
        for (JsonNode order : List.of(first, second)) {
            assertThat(orderRepository.findById(UUID.fromString(order.get("id").asText())).orElseThrow().getPayoutId()).isEqualTo(payoutId);
        }
        // the same orders are never paid out twice
        generatePayout(orgId, admin, 409);
        // a later order goes into the next payout
        checkout(cartWith(tt, 1));
        JsonNode next = generatePayout(orgId, admin, 201);
        assertThat(next.get("net").get("amount").asLong()).isEqualTo(1000L);
        assertThat(next.get("fees").get("amount").asLong()).isEqualTo(100L);

        Payout stored = payoutRepository.findById(payoutId).orElseThrow();
        assertThat(stored.getGross().getAmount() - stored.getFees().getAmount()).isEqualTo(stored.getNet().getAmount());
    }

    @Test
    void aFullyRefundedOrder_isLeftOutOfThePayout() throws Exception {
        UUID orgId = org();
        percentage("/api/v1/platform-fees/default", "10");
        UUID tt = ticketType(event(orgId), 1000);
        JsonNode kept = checkout(cartWith(tt, 1));
        JsonNode refunded = checkout(cartWith(tt, 1));
        Order refundedOrder = orderRepository.findById(UUID.fromString(refunded.get("id").asText())).orElseThrow();
        refundedOrder.setStatus(OrderStatus.REFUNDED);
        orderRepository.save(refundedOrder);

        JsonNode payout = generatePayout(orgId, admin, 201);

        assertThat(payout.get("gross").get("amount").asLong()).isEqualTo(1100L);
        assertThat(payout.get("net").get("amount").asLong()).isEqualTo(1000L);
        assertThat(orderRepository.findById(UUID.fromString(kept.get("id").asText())).orElseThrow().getPayoutId()).isNotNull();
        assertThat(orderRepository.findById(refundedOrder.getId()).orElseThrow().getPayoutId()).isNull();
    }

    @Test
    void onlyAnAdminGeneratesAPayout_andTheOrganizationOwnerCanReadIt() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID orgId = org();
        OrganizationMember member = OrganizationMember.builder().userId(ownerId).organizationId(orgId).build();
        member.getRoles().add(OrganizationRole.OWNER);
        members.add(organizationMemberRepository.save(member));
        String owner = token(User.builder().id(ownerId).email("fee-owner2@test.local").role(Role.CUSTOMER).build());
        percentage("/api/v1/platform-fees/default", "10");
        checkout(cartWith(ticketType(event(orgId), 1000), 1));

        generatePayout(orgId, customer, 403);
        generatePayout(orgId, owner, 403);
        mockMvc.perform(post("/api/v1/organizations/{id}/payouts", orgId).contentType("application/json")
                        .content("{\"periodStart\":\"2026-01-01\",\"periodEnd\":\"2026-01-02\"}"))
                .andExpect(status().isUnauthorized());
        JsonNode payout = generatePayout(orgId, admin, 201);

        mockMvc.perform(get("/api/v1/organizations/{id}/payouts", orgId).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(payout.get("id").asText()))
                .andExpect(jsonPath("$.content[0].fees.amount").value(100))
                .andExpect(jsonPath("$.content[0].net.amount").value(1000));
    }

    @Test
    void aPayoutPeriodMustBeValid() throws Exception {
        UUID orgId = org();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (String body : new String[] {
                "{\"periodStart\":\"" + today + "\",\"periodEnd\":\"" + today.minusDays(1) + "\"}",
                "{\"periodStart\":\"" + today + "\",\"periodEnd\":\"" + today.plusDays(1) + "\"}",
                "{\"periodStart\":\"" + today + "\"}",
                "{\"periodStart\":\"yesterday\",\"periodEnd\":\"today\"}"}) {
            mockMvc.perform(post("/api/v1/organizations/{id}/payouts", orgId).header("Authorization", admin)
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/organizations/{id}/payouts", UUID.randomUUID()).header("Authorization", admin)
                        .contentType("application/json").content("{\"periodStart\":\"" + today + "\",\"periodEnd\":\"" + today + "\"}"))
                .andExpect(status().isNotFound());
        generatePayout(orgId, admin, 409);   // nothing is due
    }
}
