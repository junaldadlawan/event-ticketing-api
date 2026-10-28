package com.junaldadlawan.event_ticketing_api.gatewayfee;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.TestTicketPrefixes;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The payment gateway fee setting end to end on the real database (mock payment gateway): only an admin sets it; the cart shows the fee
 * and charges it only when the customer bears it; the order snapshots it; the organizer's revenue never includes what the buyer paid
 * as gateway fee; and changing the setting later leaves placed orders alone. The setting is one global row, so the test puts the
 * original values back afterwards; rows it creates are removed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GatewayFeeIntegrationTest {

    private static final long TICKET = 100_00L;   // PHP 100.00

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<UUID> ticketTypeIds = new ArrayList<>();
    private final List<UUID> cartIds = new ArrayList<>();
    private final List<UUID> keyIds = new ArrayList<>();
    private final List<UUID> buyerIds = new ArrayList<>();
    private final List<UUID> actorIds = new ArrayList<>();

    private Map<String, Object> originalSetting;
    private UUID eventId;
    private UUID ticketTypeId;
    private UUID buyerId;
    private String buyer;
    private String admin;

    @BeforeEach
    void setUp() {
        originalSetting = jdbc.queryForMap("select percentage, flat_amount, flat_currency, payer, foreign_percentage, foreign_flat_amount from payment_gateway_fee_settings where id = 1");
        Organization org = organizationRepository.save(Organization.builder().name("Gateway Fee Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(UUID.randomUUID()).documents(List.of()).build());
        orgIds.add(org.getId());
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = eventRepository.save(Event.builder().organizationId(org.getId()).title("Gateway Fee Test Event").description("d")
                .category("music").status(EventStatus.ON_SALE).ticketPrefix(TestTicketPrefixes.free(jdbc))
                .startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC").build());
        eventIds.add(event.getId());
        eventId = event.getId();
        TicketType type = ticketTypeRepository.save(TicketType.builder().eventId(event.getId()).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(TICKET).currency("PHP").build()).quantityTotal(50).quantityAvailable(50)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS)).maxPerOrder(10).build());
        ticketTypeIds.add(type.getId());
        ticketTypeId = type.getId();
        buyerId = UUID.randomUUID();
        buyerIds.add(buyerId);
        buyer = token(Role.CUSTOMER, buyerId);
        admin = token(Role.ADMIN, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        jdbc.update("update payment_gateway_fee_settings set percentage = ?, flat_amount = ?, flat_currency = ?, payer = ?, foreign_percentage = ?, foreign_flat_amount = ? where id = 1",
                originalSetting.get("percentage"), originalSetting.get("flat_amount"), originalSetting.get("flat_currency"), originalSetting.get("payer"), originalSetting.get("foreign_percentage"), originalSetting.get("foreign_flat_amount"));
        for (UUID id : buyerIds) {
            for (UUID orderId : jdbc.queryForList("select id from orders where buyer_id = ?", UUID.class, id)) {
                jdbc.update("delete from tickets where order_id = ?", orderId);
                jdbc.update("delete from payments where order_id = ?", orderId);
                jdbc.update("delete from orders where id = ?", orderId);
            }
            jdbc.update("delete from notifications where user_id = ?", id);
            jdbc.update("delete from audit_log_entries where actor_id = ?", id);
        }
        actorIds.forEach(id -> jdbc.update("delete from audit_log_entries where actor_id = ?", id));
        keyIds.forEach(id -> jdbc.update("delete from checkout_idempotency_keys where id = ?", id));
        cartIds.forEach(id -> {
            jdbc.update("delete from cart_items where cart_id = ?", id);
            jdbc.update("delete from carts where id = ?", id);
        });
        ticketTypeIds.forEach(ticketTypeRepository::deleteById);
        eventIds.forEach(eventRepository::deleteById);
        orgIds.forEach(organizationRepository::deleteById);
    }

    // ---- fixtures ----

    private String token(Role role, UUID id) {
        if (role == Role.ADMIN) {
            actorIds.add(id);
        }
        return "Bearer " + jwtService.generateAccessToken(User.builder().id(id)
                .email("gwfee-" + UUID.randomUUID() + "@test.local").role(role).build());
    }

    private ResultActions setFee(String caller, String json) throws Exception {
        return mockMvc.perform(put("/api/v1/payment-gateway-fee").header("Authorization", caller).contentType("application/json").content(json));
    }

    /** Puts the setting row in place directly: for a payer the API no longer accepts (PLATFORM, from before it was refused). */
    private void useSetting(String percentage, long flat, String currency, String payer) {
        jdbc.update("update payment_gateway_fee_settings set percentage = ?::numeric, flat_amount = ?, flat_currency = ?, payer = ? where id = 1",
                percentage, flat, currency, payer);
    }

    private void setFeeAs(String percentage, String payer) throws Exception {
        setFee(admin, "{\"percentage\":" + percentage + ",\"payer\":\"" + payer + "\"}").andExpect(status().isOk());
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private UUID cartWithOneTicket() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/carts").header("Authorization", buyer)).andExpect(status().isCreated()).andReturn();
        UUID cartId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        cartIds.add(cartId);
        mockMvc.perform(post("/api/v1/carts/{id}/items", cartId).header("Authorization", buyer).contentType("application/json")
                .content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":1}")).andExpect(status().isCreated());
        return cartId;
    }

    private JsonNode cart(UUID cartId) throws Exception {
        return body(mockMvc.perform(get("/api/v1/carts/{id}", cartId).header("Authorization", buyer)).andExpect(status().isOk()));
    }

    private JsonNode checkout(UUID cartId) throws Exception {
        UUID key = UUID.randomUUID();
        keyIds.add(key);
        return body(mockMvc.perform(post("/api/v1/carts/{id}/checkout", cartId).header("Authorization", buyer).header("Idempotency-Key", key.toString())
                .contentType("application/json").content("{\"paymentMethodToken\":\"tok_ok\"}")).andExpect(status().isCreated()));
    }

    /** 4.02% of PHP 100.00 plus whatever the platform fee is, as the app rounds it (half up to the centavo). */
    private long expectedFee(JsonNode cart, String percentage) {
        long base = TICKET + cart.get("platformFee").get("amount").asLong();
        return new BigDecimal(base).multiply(new BigDecimal(percentage)).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    // ---- the setting ----

    @Test
    void theFeeCheck_inMockMode_showsTheSettingAndNoSamples_toAnAdminOnly_andRefusesABadLimit() throws Exception {
        setFee(admin, "{\"percentage\":3.5,\"flatAmount\":{\"amount\":1500,\"currency\":\"PHP\"},\"payer\":\"CUSTOMER\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/payment-gateway-fee/check").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("MOCK")).andExpect(jsonPath("$.configured.percentage").value(3.5))
                .andExpect(jsonPath("$.configured.flatAmount.amount").value(1500)).andExpect(jsonPath("$.configured.payer").value("CUSTOMER"))
                .andExpect(jsonPath("$.samples").isEmpty()).andExpect(jsonPath("$.allSamplesMatchConfigured").value(false))
                .andExpect(jsonPath("$.suggestedSetting").doesNotExist()).andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("mock mode")));
        mockMvc.perform(get("/api/v1/payment-gateway-fee/check").param("limit", "0").header("Authorization", admin)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/payment-gateway-fee/check").header("Authorization", buyer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/payment-gateway-fee/check")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAnAdminReadsAndChangesTheSetting() throws Exception {
        String json = "{\"percentage\":4.02,\"payer\":\"CUSTOMER\"}";

        setFee(buyer, json).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/payment-gateway-fee").header("Authorization", buyer)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/payment-gateway-fee").contentType("application/json").content(json)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/payment-gateway-fee")).andExpect(status().isUnauthorized());

        setFee(admin, "{\"percentage\":4.02,\"flatAmount\":{\"amount\":1339,\"currency\":\"PHP\"},\"payer\":\"CUSTOMER\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.percentage").value(4.02)).andExpect(jsonPath("$.flatAmount.amount").value(1339))
                .andExpect(jsonPath("$.payer").value("CUSTOMER"));
        mockMvc.perform(get("/api/v1/payment-gateway-fee").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.payer").value("CUSTOMER")).andExpect(jsonPath("$.flatAmount.currency").value("PHP"));
    }

    @Test
    void aForeignRateCanBeSetBesideTheDomesticOne_shownBack_andClearedByLeavingItOut() throws Exception {
        setFee(admin, "{\"percentage\":3.5,\"flatAmount\":{\"amount\":1500,\"currency\":\"PHP\"},\"payer\":\"CUSTOMER\","
                + "\"foreignPercentage\":4.5,\"foreignFlatAmount\":{\"amount\":1500,\"currency\":\"PHP\"}}").andExpect(status().isOk())
                .andExpect(jsonPath("$.percentage").value(3.5)).andExpect(jsonPath("$.foreignPercentage").value(4.5))
                .andExpect(jsonPath("$.foreignFlatAmount.amount").value(1500)).andExpect(jsonPath("$.foreignFlatAmount.currency").value("PHP"));
        mockMvc.perform(get("/api/v1/payment-gateway-fee").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.foreignPercentage").value(4.5));
        // the cart charges the domestic rate: the card is not known yet
        JsonNode cart = cart(cartWithOneTicket());
        long base = TICKET + cart.get("platformFee").get("amount").asLong();
        assertThat(cart.get("gatewayFee").get("amount").asLong())
                .isEqualTo(new BigDecimal(base).multiply(new BigDecimal("3.5")).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP).longValueExact() + 1500L);

        setFee(admin, "{\"percentage\":3.5,\"payer\":\"CUSTOMER\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.foreignPercentage").doesNotExist()).andExpect(jsonPath("$.foreignFlatAmount").doesNotExist());
    }

    @Test
    void aForeignRateThatDoesNotFit_isRefused_andNothingChanges() throws Exception {
        for (String json : new String[] {
                "{\"percentage\":3.5,\"flatAmount\":{\"amount\":1500,\"currency\":\"PHP\"},\"payer\":\"CUSTOMER\",\"foreignFlatAmount\":{\"amount\":1500,\"currency\":\"USD\"}}",
                "{\"percentage\":3.5,\"payer\":\"CUSTOMER\",\"foreignPercentage\":100.01}",
                "{\"percentage\":3.5,\"payer\":\"CUSTOMER\",\"foreignPercentage\":-1}",
                "{\"percentage\":3.5,\"payer\":\"CUSTOMER\",\"foreignPercentage\":4.5678}",
                "{\"percentage\":3.5,\"payer\":\"CUSTOMER\",\"foreignFlatAmount\":{\"amount\":-5,\"currency\":\"PHP\"}}"}) {
            setFee(admin, json).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select payer from payment_gateway_fee_settings where id = 1", String.class)).isEqualTo(originalSetting.get("payer"));
    }

    @Test
    void theFeeHasTwoNumbers_aThreeDecimalPercentageAndAFixedAmount_likePayMongosCardRate() throws Exception {
        setFee(admin, "{\"percentage\":3.125,\"flatAmount\":{\"amount\":1339,\"currency\":\"PHP\"},\"payer\":\"CUSTOMER\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.percentage").value(3.125)).andExpect(jsonPath("$.flatAmount.amount").value(1339));
        UUID cartId = cartWithOneTicket();

        JsonNode cart = cart(cartId);

        long base = TICKET + cart.get("platformFee").get("amount").asLong();
        long expected = new BigDecimal(base).multiply(new BigDecimal("3.125")).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP).longValueExact() + 1339L;
        assertThat(cart.get("gatewayFee").get("amount").asLong()).isEqualTo(expected);
        JsonNode order = checkout(cartId);
        assertThat(order.get("gatewayFee").get("amount").asLong()).isEqualTo(expected);
        assertThat(new BigDecimal(jdbc.queryForObject("select gateway_fee_percentage from orders where buyer_id = ?", Object.class, buyerId).toString()))
                .isEqualByComparingTo("3.125");   // the order keeps all three decimals
    }

    @Test
    void anInvalidSettingIsRefused_andNothingChanges() throws Exception {
        for (String json : new String[] {
                "{\"percentage\":100.01,\"payer\":\"CUSTOMER\"}",
                "{\"percentage\":-1,\"payer\":\"CUSTOMER\"}",
                "{\"percentage\":1.2345,\"payer\":\"CUSTOMER\"}",
                "{\"payer\":\"CUSTOMER\"}",
                "{\"percentage\":4,\"payer\":\"EVERYONE\"}",
                "{\"percentage\":4}",
                "{\"percentage\":4,\"flatAmount\":{\"amount\":-5,\"currency\":\"PHP\"},\"payer\":\"CUSTOMER\"}",
                "{\"percentage\":4,\"flatAmount\":{\"amount\":5,\"currency\":\"php\"},\"payer\":\"CUSTOMER\"}"}) {
            setFee(admin, json).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForMap("select percentage, payer from payment_gateway_fee_settings where id = 1"))
                .containsEntry("payer", originalSetting.get("payer"));
    }

    // ---- who pays ----

    @Test
    void whenTheCustomerPays_theCartShowsTheFee_andChargesIt_andTheOrganizersRevenueExcludesIt() throws Exception {
        setFeeAs("4.02", "CUSTOMER");
        UUID cartId = cartWithOneTicket();

        JsonNode cart = cart(cartId);
        long fee = expectedFee(cart, "4.02");
        assertThat(fee).isPositive();
        assertThat(cart.get("gatewayFee").get("amount").asLong()).isEqualTo(fee);
        long platformFee = cart.get("platformFee").get("amount").asLong();
        assertThat(cart.get("total").get("amount").asLong()).isEqualTo(TICKET + platformFee + fee);

        JsonNode order = checkout(cartId);
        assertThat(order.get("gatewayFee").get("amount").asLong()).isEqualTo(fee);
        assertThat(order.get("total").get("amount").asLong()).isEqualTo(TICKET + platformFee + fee);
        Map<String, Object> row = jdbc.queryForMap("select gateway_fee_amount, gateway_fee_payer, gateway_fee_percentage from orders where buyer_id = ?", buyerId);
        assertThat(row.get("gateway_fee_amount")).isEqualTo(fee);
        assertThat(row.get("gateway_fee_payer")).isEqualTo("CUSTOMER");
        assertThat(new BigDecimal(row.get("gateway_fee_percentage").toString())).isEqualByComparingTo("4.02");

        // the event's revenue is the ticket price only: neither fee is the organizer's money
        mockMvc.perform(get("/api/v1/events/{id}/analytics", eventId).header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue.amount").value(TICKET));
    }

    @Test
    void whenTheOrganizationPays_theBuyerSeesNoFee_andTheOrderRecordsIt() throws Exception {
        setFeeAs("4.02", "ORGANIZATION");
        UUID cartId = cartWithOneTicket();

        JsonNode cart = cart(cartId);
        long platformFee = cart.get("platformFee").get("amount").asLong();
        assertThat(cart.get("gatewayFee").get("amount").asLong()).isZero();
        assertThat(cart.get("total").get("amount").asLong()).isEqualTo(TICKET + platformFee);

        JsonNode order = checkout(cartId);
        assertThat(order.get("gatewayFee").get("amount").asLong()).isZero();
        assertThat(order.get("total").get("amount").asLong()).isEqualTo(TICKET + platformFee);
        Map<String, Object> row = jdbc.queryForMap("select gateway_fee_amount, gateway_fee_payer from orders where buyer_id = ?", buyerId);
        assertThat((Long) row.get("gateway_fee_amount")).isEqualTo(expectedFee(cart, "4.02"));   // kept for the payout deduction
        assertThat(row.get("gateway_fee_payer")).isEqualTo("ORGANIZATION");
        mockMvc.perform(get("/api/v1/events/{id}/analytics", eventId).header("Authorization", admin))
                .andExpect(jsonPath("$.revenue.amount").value(TICKET));   // the ticket price only, as before
    }

    @Test
    void anOldSettingThatSaidPlatform_stillLeavesTheBuyerUnaffected() throws Exception {
        // PLATFORM can no longer be set; a row stored before that (or an order placed then) is still handled
        useSetting("4.02", 0, "PHP", "PLATFORM");
        UUID cartId = cartWithOneTicket();

        JsonNode cart = cart(cartId);
        assertThat(cart.get("gatewayFee").get("amount").asLong()).isZero();
        long platformFee = cart.get("platformFee").get("amount").asLong();
        assertThat(cart.get("total").get("amount").asLong()).isEqualTo(TICKET + platformFee);
        checkout(cartId);
        assertThat(jdbc.queryForObject("select gateway_fee_payer from orders where buyer_id = ?", String.class, buyerId)).isEqualTo("PLATFORM");
    }

    @Test
    void aFlatPartInPhpIsAdded_butOneInAnotherCurrencyIsSkipped() throws Exception {
        setFee(admin, "{\"percentage\":0,\"flatAmount\":{\"amount\":1339,\"currency\":\"PHP\"},\"payer\":\"CUSTOMER\"}").andExpect(status().isOk());
        UUID cartId = cartWithOneTicket();
        assertThat(cart(cartId).get("gatewayFee").get("amount").asLong()).isEqualTo(1339L);

        setFee(admin, "{\"percentage\":0,\"flatAmount\":{\"amount\":1339,\"currency\":\"USD\"},\"payer\":\"CUSTOMER\"}").andExpect(status().isOk());
        assertThat(cart(cartId).get("gatewayFee").get("amount").asLong()).isZero();
    }

    @Test
    void changingTheSettingLater_doesNotRewriteAnOrderAlreadyPlaced() throws Exception {
        setFeeAs("4.02", "CUSTOMER");
        UUID cartId = cartWithOneTicket();
        JsonNode placed = checkout(cartId);
        long feeThen = placed.get("gatewayFee").get("amount").asLong();

        setFeeAs("0", "CUSTOMER");

        Map<String, Object> row = jdbc.queryForMap("select gateway_fee_amount, gateway_fee_payer, total_amount from orders where buyer_id = ?", buyerId);
        assertThat(row.get("gateway_fee_amount")).isEqualTo(feeThen);
        assertThat(row.get("gateway_fee_payer")).isEqualTo("CUSTOMER");
        assertThat(row.get("total_amount")).isEqualTo(placed.get("total").get("amount").asLong());
    }

    @Test
    void withTheDefaultSetting_nothingChanges() throws Exception {
        setFeeAs("0", "CUSTOMER");
        UUID cartId = cartWithOneTicket();

        JsonNode cart = cart(cartId);

        assertThat(cart.get("gatewayFee").get("amount").asLong()).isZero();
        assertThat(cart.get("total").get("amount").asLong()).isEqualTo(TICKET + cart.get("platformFee").get("amount").asLong());
    }
}
