package com.junaldadlawan.event_ticketing_api.cart;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.repository.CheckoutIdempotencyKeyRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PaymentRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.platformfee.entity.PlatformFeeRule;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeScope;
import com.junaldadlawan.event_ticketing_api.platformfee.repository.PlatformFeeRuleRepository;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pricing and paying only some of the items of a cart (mock gateway, real DB). Platform fee: 10% plus a fixed 50 per TICKET; the gateway fee is
 * switched off. The cart holds item A (2 x 1,000) and item B (1 x 3,000). The platform's own default rule and the gateway setting are set aside
 * and put back.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CartSelectionIntegrationTest {

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
    private PromoCodeRepository promoCodes;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> keyIds = new ArrayList<>();
    private final List<UUID> cartIds = new ArrayList<>();
    private final List<UUID> promoIds = new ArrayList<>();
    private UUID orgId;
    private UUID eventId;
    private UUID ticketTypeA;
    private UUID ticketTypeB;
    private UUID setAsideDefault;
    private java.util.Map<String, Object> originalGatewayFee;
    private Instant startedAt;
    private String admin;
    private String customer;

    private UUID cartId;
    private UUID itemA;
    private UUID itemB;

    @BeforeEach
    void setUp() throws Exception {
        startedAt = Instant.now().minusSeconds(1);
        originalGatewayFee = jdbc.queryForMap("select percentage, flat_amount, flat_currency, payer, foreign_percentage, foreign_flat_amount from payment_gateway_fee_settings where id = 1");
        jdbc.update("update payment_gateway_fee_settings set percentage = 0, flat_amount = 0, payer = 'CUSTOMER', foreign_percentage = null, foreign_flat_amount = null where id = 1");
        PlatformFeeRule existing = ruleRepository.findByScopeAndScopeIdIsNullAndDeletedAtIsNull(FeeScope.PLATFORM).orElse(null);
        if (existing != null) {
            setAsideDefault = existing.getId();
            jdbc.update("update platform_fee_rules set deleted_at = now() where id = ?", setAsideDefault);
        }
        admin = "Bearer " + jwtService.generateAccessToken(User.builder().id(UUID.randomUUID()).email("sel-admin-" + UUID.randomUUID() + "@test.local").role(Role.ADMIN).build());
        customer = "Bearer " + jwtService.generateAccessToken(User.builder().id(UUID.randomUUID()).email("sel-buyer-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build());
        mockMvc.perform(put("/api/v1/platform-fees/default").header("Authorization", admin).contentType("application/json")
                .content("{\"percentage\":10,\"flatAmount\":{\"amount\":50,\"currency\":\"USD\"},\"payer\":\"CUSTOMER\"}")).andExpect(status().isOk());

        orgId = organizationRepository.save(Organization.builder().name("Selection Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(UUID.randomUUID()).documents(List.of()).build()).getId();
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        eventId = eventRepository.save(Event.builder().organizationId(orgId).title("Selection Test Event").description("d").category("music")
                .status(EventStatus.ON_SALE).ticketPrefix("S" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
        ticketTypeA = ticketType("A", 1_000);
        ticketTypeB = ticketType("B", 3_000);

        cartId = UUID.fromString(body(mockMvc.perform(post("/api/v1/carts").header("Authorization", customer)).andExpect(status().isCreated()).andReturn()).get("id").asText());
        cartIds.add(cartId);
        addItem(ticketTypeA, 2);
        JsonNode both = addItem(ticketTypeB, 1);
        for (JsonNode item : both.get("items")) {
            if (item.get("ticketTypeId").asText().equals(ticketTypeA.toString())) {
                itemA = UUID.fromString(item.get("id").asText());
            } else {
                itemB = UUID.fromString(item.get("id").asText());
            }
        }
    }

    @AfterEach
    void tearDown() {
        for (UUID id : orderIds) {
            ticketRepository.findByOrderId(id).forEach(ticket -> ticketRepository.deleteById(ticket.getId()));
            paymentRepository.findByOrderId(id).forEach(payment -> paymentRepository.deleteById(payment.getId()));
            orderRepository.deleteById(id);
        }
        keyIds.forEach(idempotencyKeyRepository::deleteById);
        cartIds.forEach(id -> cartItemRepository.findByCartId(id).forEach(cartItemRepository::delete));
        cartIds.forEach(cartRepository::deleteById);
        promoIds.forEach(promoCodes::deleteById);
        jdbc.update("delete from ticket_types where event_id = ?", eventId);
        eventRepository.deleteById(eventId);
        organizationRepository.deleteById(orgId);
        jdbc.update("delete from platform_fee_rules where created_at >= ?", java.sql.Timestamp.from(startedAt));
        if (setAsideDefault != null) {
            jdbc.update("update platform_fee_rules set deleted_at = null where id = ?", setAsideDefault);
        }
        jdbc.update("update payment_gateway_fee_settings set percentage = ?, flat_amount = ?, flat_currency = ?, payer = ?, foreign_percentage = ?, foreign_flat_amount = ? where id = 1",
                originalGatewayFee.get("percentage"), originalGatewayFee.get("flat_amount"), originalGatewayFee.get("flat_currency"), originalGatewayFee.get("payer"), originalGatewayFee.get("foreign_percentage"), originalGatewayFee.get("foreign_flat_amount"));
    }

    // ---- fixtures ----

    private UUID ticketType(String name, long price) {
        return ticketTypeRepository.save(TicketType.builder().eventId(eventId).name(name).kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(price).currency("USD").build()).quantityTotal(50).quantityAvailable(50)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS)).maxPerOrder(10).build()).getId();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode addItem(UUID ticketTypeId, int quantity) throws Exception {
        return body(mockMvc.perform(post("/api/v1/carts/{id}/items", cartId).header("Authorization", customer).contentType("application/json")
                .content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":" + quantity + "}")).andExpect(status().isCreated()).andReturn());
    }

    private String ids(UUID... items) {
        return String.join(",", java.util.Arrays.stream(items).map(UUID::toString).toList());
    }

    private JsonNode quote(UUID... items) throws Exception {
        String url = "/api/v1/carts/{id}" + (items.length == 0 ? "" : "?itemIds=" + ids(items));
        return body(mockMvc.perform(get(url, cartId).header("Authorization", customer)).andExpect(status().isOk()).andReturn());
    }

    private MvcResult checkout(UUID key, int expected, String itemIdsJson) throws Exception {
        keyIds.add(key);
        MvcResult result = mockMvc.perform(post("/api/v1/carts/{id}/checkout", cartId).header("Authorization", customer)
                .header("Idempotency-Key", key.toString()).contentType("application/json")
                .content("{\"paymentMethodToken\":\"tok_ok\"" + (itemIdsJson == null ? "" : ",\"itemIds\":" + itemIdsJson) + "}"))
                .andExpect(status().is(expected)).andReturn();
        if (expected == 201) {
            orderIds.add(UUID.fromString(body(result).get("id").asText()));
        }
        return result;
    }

    private JsonNode pay(UUID... items) throws Exception {
        return body(checkout(UUID.randomUUID(), 201, items.length == 0 ? null : "[\"" + String.join("\",\"", java.util.Arrays.stream(items).map(UUID::toString).toList()) + "\"]"));
    }

    private long total(JsonNode node) {
        return node.get("total").get("amount").asLong();
    }

    // ---- tests ----

    @Test
    void theQuoteForASelection_isWhatCheckoutThenCharges_andTheRestStaysInTheCartWithItsHold() throws Exception {
        Instant holdOfB = cartItemRepository.findById(itemB).orElseThrow().getHoldExpiresAt();

        JsonNode selection = quote(itemA);
        assertThat(selection.get("items")).hasSize(1);
        assertThat(selection.get("platformFee").get("amount").asLong()).isEqualTo(300);   // 10% of 2,000 + 50 x 2 tickets
        assertThat(total(selection)).isEqualTo(2_300);
        JsonNode wholeCart = quote();
        assertThat(wholeCart.get("items")).hasSize(2);
        assertThat(total(wholeCart)).isEqualTo(5_650);   // 5,000 + 10% + 50 x 3 tickets

        JsonNode order = pay(itemA);

        assertThat(total(order)).isEqualTo(total(selection));
        assertThat(order.get("tickets")).hasSize(2);
        assertThat(orderRepository.findById(orderIds.get(0)).orElseThrow().getPlatformFeeAmount()).isEqualTo(300);
        // item B is still in the cart, hold unchanged, and the cart is now priced for it alone
        assertThat(cartItemRepository.findById(itemA)).isEmpty();
        assertThat(cartItemRepository.findById(itemB).orElseThrow().getHoldExpiresAt()).isEqualTo(holdOfB);
        assertThat(total(quote())).isEqualTo(3_350);   // 3,000 + 300 + 50 x 1 ticket
    }

    @Test
    void payingTwoSelectionsInTurn_makesTwoOrdersAndEmptiesTheCart() throws Exception {
        JsonNode first = pay(itemA);
        JsonNode second = pay(itemB);

        assertThat(first.get("id").asText()).isNotEqualTo(second.get("id").asText());
        assertThat(total(first)).isEqualTo(2_300);
        assertThat(total(second)).isEqualTo(3_350);
        assertThat(cartItemRepository.findByCartId(cartId)).isEmpty();
        // the fixed 50 is per ticket, so two orders cost the same as one: 300 + 350 = 650
        assertThat(orderRepository.findById(orderIds.get(0)).orElseThrow().getPlatformFeeAmount()
                + orderRepository.findById(orderIds.get(1)).orElseThrow().getPlatformFeeAmount()).isEqualTo(650);
    }

    @Test
    void omittingItemIds_stillPaysTheWholeCart() throws Exception {
        JsonNode order = pay();

        assertThat(total(order)).isEqualTo(5_650);
        assertThat(order.get("tickets")).hasSize(3);
        assertThat(cartItemRepository.findByCartId(cartId)).isEmpty();
    }

    @Test
    void anItemNotInTheCart_isA400_andAnExpiredHoldOnASelectedItemIsRejected_theOthersAreNotAffected() throws Exception {
        mockMvc.perform(get("/api/v1/carts/{id}?itemIds=" + UUID.randomUUID(), cartId).header("Authorization", customer)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/carts/{id}?itemIds=", cartId).header("Authorization", customer)).andExpect(status().isBadRequest());
        checkout(UUID.randomUUID(), 400, "[\"" + UUID.randomUUID() + "\"]");
        checkout(UUID.randomUUID(), 400, "[]");

        jdbc.update("update cart_items set hold_expires_at = now() - interval '1 minute' where id = ?", itemA);

        mockMvc.perform(get("/api/v1/carts/{id}?itemIds=" + itemA, cartId).header("Authorization", customer)).andExpect(status().isConflict());
        checkout(UUID.randomUUID(), 410, "[\"" + itemA + "\"]");   // as for a whole cart: an expired hold is "gone"
        // B was never selected, so it can still be quoted and paid
        assertThat(total(quote(itemB))).isEqualTo(3_350);
        assertThat(total(pay(itemB))).isEqualTo(3_350);
    }

    @Test
    void aPromoCodeAppliesOnlyWhenTheSelectionFitsIt_otherwiseItStaysOnTheCart() throws Exception {
        // 500 off, valid for ticket type A only; applied while the cart held only A, then B was added
        removeItemB();
        PromoCode promo = promoCodes.save(PromoCode.builder().eventId(eventId).code("SELA").discountType(DiscountType.FIXED)
                .discountValue(BigDecimal.valueOf(500)).applicableTicketTypeIds(Set.of(ticketTypeA))
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS)).validUntil(Instant.now().plus(1, ChronoUnit.DAYS)).build());
        promoIds.add(promo.getId());
        mockMvc.perform(post("/api/v1/carts/{id}/promo-code", cartId).header("Authorization", customer).contentType("application/json")
                .content("{\"code\":\"SELA\"}")).andExpect(status().isOk());
        itemB = UUID.fromString(addItem(ticketTypeB, 1).get("items").findValues("id").stream()
                .map(JsonNode::asText).filter(id -> !id.equals(itemA.toString())).findFirst().orElseThrow());

        JsonNode forA = quote(itemA);
        assertThat(forA.get("appliedPromoCode").get("code").asText()).isEqualTo("SELA");
        assertThat(total(forA)).isEqualTo(1_750);   // 2,000 - 500 = 1,500 + 150 + 50 x 2 tickets
        JsonNode forB = quote(itemB);
        assertThat(forB.get("appliedPromoCode").isNull()).isTrue();
        assertThat(total(forB)).isEqualTo(3_350);

        // paying B leaves the code on the cart; the order does not carry it
        JsonNode orderB = pay(itemB);
        assertThat(total(orderB)).isEqualTo(3_350);
        assertThat(orderRepository.findById(orderIds.get(0)).orElseThrow().getPromoCode()).isNull();
        assertThat(quote().get("appliedPromoCode").get("code").asText()).isEqualTo("SELA");

        // paying A uses it
        JsonNode orderA = pay(itemA);
        assertThat(total(orderA)).isEqualTo(1_750);
        Order saved = orderRepository.findById(orderIds.get(1)).orElseThrow();
        assertThat(saved.getPromoCode()).isEqualTo("SELA");
        assertThat(cartRepository.findById(cartId).orElseThrow().getPromoCodeId()).isNull();
    }

    @Test
    void theSameKeyWithAnotherSelection_isA409_andTheSameSelectionReplaysTheSameOrder() throws Exception {
        UUID key = UUID.randomUUID();
        JsonNode order = body(checkout(key, 201, "[\"" + itemA + "\"]"));

        checkout(key, 409, "[\"" + itemB + "\"]");
        checkout(key, 409, null);   // the whole cart is another selection too
        JsonNode replay = body(checkout(key, 201, "[\"" + itemA + "\"]"));

        assertThat(replay.get("id").asText()).isEqualTo(order.get("id").asText());
        assertThat(orderRepository.findAll().stream().filter(o -> orderIds.contains(o.getId())).count()).isEqualTo(1);
        assertThat(cartItemRepository.findById(itemB)).isPresent();
    }

    @Test
    void aFlatRuleCharges50ForEveryTicket_oneTicketOrThree() throws Exception {
        mockMvc.perform(put("/api/v1/platform-fees/default").header("Authorization", admin).contentType("application/json")
                .content("{\"flatAmount\":{\"amount\":50,\"currency\":\"USD\"},\"payer\":\"CUSTOMER\"}")).andExpect(status().isOk());

        assertThat(quote(itemB).get("platformFee").get("amount").asLong()).isEqualTo(50);    // 1 ticket
        assertThat(quote(itemA).get("platformFee").get("amount").asLong()).isEqualTo(100);   // 2 tickets
        JsonNode all = quote();                                                              // 3 tickets
        assertThat(all.get("platformFee").get("amount").asLong()).isEqualTo(150);
        assertThat(total(all)).isEqualTo(5_150);
        assertThat(total(pay())).isEqualTo(5_150);   // the charge is the quote
        assertThat(orderRepository.findById(orderIds.get(0)).orElseThrow().getPlatformFeeAmount()).isEqualTo(150);
    }

    @Test
    void anOrderAlreadyPlaced_keepsItsFee_whenTheRuleChangesAfterwards() throws Exception {
        pay(itemA);   // 10% of 2,000 + 50 x 2 tickets = 300
        mockMvc.perform(put("/api/v1/platform-fees/default").header("Authorization", admin).contentType("application/json")
                .content("{\"flatAmount\":{\"amount\":400,\"currency\":\"USD\"},\"payer\":\"CUSTOMER\"}")).andExpect(status().isOk());

        assertThat(orderRepository.findById(orderIds.get(0)).orElseThrow().getPlatformFeeAmount()).isEqualTo(300);   // not recalculated
        assertThat(quote().get("platformFee").get("amount").asLong()).isEqualTo(400);   // the remaining 1 ticket under the new rule
    }

    @Test
    void aMergedLine_keepsThePerTicketFeeRight() throws Exception {
        JsonNode merged = addItem(ticketTypeA, 1);   // joins the line of A: 3 tickets of A, 1 of B

        assertThat(merged.get("items")).hasSize(2);
        assertThat(total(quote())).isEqualTo(6_800);      // 6,000 + 10% (600) + 50 x 4 tickets
        assertThat(total(quote(itemA))).isEqualTo(3_450);   // 3,000 + 300 + 50 x 3 tickets
        assertThat(total(pay(itemA))).isEqualTo(3_450);     // the charge is the quote
    }

    private void removeItemB() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/carts/{id}/items/{item}", cartId, itemB)
                .header("Authorization", customer)).andExpect(status().isNoContent());
    }
}
