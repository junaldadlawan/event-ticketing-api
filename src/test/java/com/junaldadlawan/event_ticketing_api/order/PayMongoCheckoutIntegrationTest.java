package com.junaldadlawan.event_ticketing_api.order;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.gateway.PayMongoPaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.order.gateway.PayMongoWebhookVerifier;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentResult;
import com.junaldadlawan.event_ticketing_api.order.repository.CheckoutIdempotencyKeyRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PaymentRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checkout with PayMongo end to end on the real database: starting a checkout creates a payment intent and a pending checkout but
 * NO order; a correctly signed {@code payment.paid} webhook then issues the order and tickets exactly once; a failed payment frees
 * the buyer to try again. PayMongo itself is stubbed (the gateway's two outgoing calls); the webhook goes through the real security
 * chain and signature check.
 */
@SpringBootTest(properties = {"payments.gateway=paymongo", "payments.paymongo.secret-key=sk_test_not_a_real_key",
        "payments.paymongo.webhook-secret=whsk_test_secret"})
@AutoConfigureMockMvc
class PayMongoCheckoutIntegrationTest {

    @MockitoSpyBean
    private PayMongoPaymentGatewayClient gateway;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PayMongoWebhookVerifier verifier;
    @Autowired
    private PaymentGatewayClient paymentGatewayClient;
    @Autowired
    private JdbcTemplate jdbc;
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

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<UUID> ticketTypeIds = new ArrayList<>();
    private final List<UUID> cartIds = new ArrayList<>();
    private final List<UUID> keyIds = new ArrayList<>();
    private final List<UUID> buyerIds = new ArrayList<>();

    private UUID ticketTypeId;
    private UUID buyerId;
    private String buyer;

    @BeforeEach
    void setUp() throws Exception {
        Organization org = organizationRepository.save(Organization.builder().name("PayMongo Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(UUID.randomUUID()).documents(List.of()).build());
        orgIds.add(org.getId());
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = eventRepository.save(Event.builder().organizationId(org.getId()).title("PayMongo Test Event").description("d")
                .category("music").status(EventStatus.ON_SALE).ticketPrefix("P" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC").build());
        eventIds.add(event.getId());
        TicketType type = ticketTypeRepository.save(TicketType.builder().eventId(event.getId()).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(100_00).currency("PHP").build()).quantityTotal(50).quantityAvailable(50)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS)).maxPerOrder(10).build());
        ticketTypeIds.add(type.getId());
        ticketTypeId = type.getId();
        buyerId = UUID.randomUUID();
        buyerIds.add(buyerId);
        buyer = token(buyerId);

        doReturn(new PaymentGatewayClient.PaymentIntentInfo("pi_it_" + UUID.randomUUID().toString().replace("-", ""), "client_key_secret"))
                .when(gateway).createPaymentIntent(any(), any(), any());
        doReturn(PaymentResult.success("ref_it")).when(gateway).refund(any(), any());
    }

    @AfterEach
    void tearDown() {
        for (UUID id : buyerIds) {
            for (UUID orderId : jdbc.queryForList("select id from orders where buyer_id = ?", UUID.class, id)) {
                jdbc.update("delete from tickets where order_id = ?", orderId);
                jdbc.update("delete from payments where order_id = ?", orderId);
                jdbc.update("delete from orders where id = ?", orderId);
            }
            jdbc.update("delete from pending_checkouts where buyer_id = ?", id);
        }
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

    private String token(UUID id) {
        return "Bearer " + jwtService.generateAccessToken(User.builder().id(id)
                .email("paymongo-it-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build());
    }

    private UUID cartWithOneTicket(String caller) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/carts").header("Authorization", caller)).andExpect(status().isCreated()).andReturn();
        UUID cartId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        cartIds.add(cartId);
        mockMvc.perform(post("/api/v1/carts/{id}/items", cartId).header("Authorization", caller).contentType("application/json")
                .content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":1}")).andExpect(status().isCreated());
        return cartId;
    }

    private ResultActions start(String caller, UUID cartId, UUID key) throws Exception {
        keyIds.add(key);
        return mockMvc.perform(post("/api/v1/carts/{id}/checkout", cartId).header("Authorization", caller)
                .header("Idempotency-Key", key.toString()).contentType("application/json").content("{}"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String webhookBody(String type, String intentId, String paymentId, long amount, String currency) {
        return "{\"data\":{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"event\",\"attributes\":{\"type\":\"" + type
                + "\",\"livemode\":false,\"data\":{\"id\":\"" + paymentId + "\",\"type\":\"payment\",\"attributes\":{\"amount\":" + amount
                + ",\"currency\":\"" + currency + "\",\"payment_intent_id\":\"" + intentId + "\",\"status\":\"paid\"}}}}}";
    }

    private ResultActions webhook(String body, boolean validSignature) throws Exception {
        String timestamp = "1700000000";
        String signature = validSignature ? verifier.sign(timestamp + "." + body) : "0".repeat(64);
        return mockMvc.perform(post("/api/v1/webhooks/paymongo").contentType("application/json")
                .header("Paymongo-Signature", "t=" + timestamp + ",te=" + signature + ",li=").content(body.getBytes(StandardCharsets.UTF_8)));
    }

    private int ordersOfBuyer() {
        return jdbc.queryForObject("select count(*) from orders where buyer_id = ?", Integer.class, buyerId);
    }

    // ---- tests ----

    @Test
    void thePayMongoGatewayIsTheOneInUse() {
        assertThat(paymentGatewayClient).isInstanceOf(PayMongoPaymentGatewayClient.class);
    }

    @Test
    void startingACheckout_createsAPendingCheckout_andNoOrder_untilTheWebhookSaysPaid() throws Exception {
        UUID cartId = cartWithOneTicket(buyer);

        ResultActions started = start(buyer, cartId, UUID.randomUUID()).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.clientKey").value("client_key_secret"))
                .andExpect(jsonPath("$.currency").value("PHP"))
                .andExpect(jsonPath("$.orderId").doesNotExist());
        JsonNode body = json(started);
        verify(gateway).createPaymentIntent(any(), eq("Event ticket order"), any());
        assertThat(body.get("amount").asLong()).isGreaterThanOrEqualTo(100_00L);   // the ticket plus any platform fee
        assertThat(ordersOfBuyer()).isZero();   // BR-CART-002: no order before the payment succeeded
    }

    @Test
    void aSignedPaidWebhook_issuesTheOrderAndTicketsOnce_andTheBuyerSeesItPaid() throws Exception {
        UUID cartId = cartWithOneTicket(buyer);
        JsonNode started = json(start(buyer, cartId, UUID.randomUUID()).andExpect(status().isAccepted()));
        UUID checkoutId = UUID.fromString(started.get("checkoutId").asText());
        String intentId = jdbc.queryForObject("select provider_intent_id from pending_checkouts where id = ?", String.class, checkoutId);
        String paid = webhookBody("payment.paid", intentId, "pay_it_1", started.get("amount").asLong(), "PHP");

        webhook(paid, true).andExpect(status().isOk());
        webhook(paid, true).andExpect(status().isOk());   // PayMongo may deliver it twice

        assertThat(ordersOfBuyer()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/checkouts/{id}", checkoutId).header("Authorization", buyer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.orderId").exists());
        assertThat(jdbc.queryForObject("select gateway_ref from payments p join orders o on o.id = p.order_id where o.buyer_id = ?", String.class, buyerId))
                .isEqualTo("pay_it_1");   // the payment id a refund needs
        assertThat(jdbc.queryForObject("select count(*) from tickets t join orders o on o.id = t.order_id where o.buyer_id = ?", Integer.class, buyerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from cart_items where cart_id = ?", Integer.class, cartId)).isZero();
        verify(gateway, never()).refund(any(), any());
    }

    @Test
    void aWebhookWithABadSignature_isRejected_andChangesNothing() throws Exception {
        UUID cartId = cartWithOneTicket(buyer);
        JsonNode started = json(start(buyer, cartId, UUID.randomUUID()).andExpect(status().isAccepted()));
        String intentId = jdbc.queryForObject("select provider_intent_id from pending_checkouts where id = ?", String.class,
                UUID.fromString(started.get("checkoutId").asText()));

        webhook(webhookBody("payment.paid", intentId, "pay_forged", started.get("amount").asLong(), "PHP"), false).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/webhooks/paymongo").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());

        assertThat(ordersOfBuyer()).isZero();
        assertThat(jdbc.queryForObject("select status from pending_checkouts where provider_intent_id = ?", String.class, intentId)).isEqualTo("PENDING");
    }

    @Test
    void aPaymentThatDoesNotMatchTheOrder_isRefunded_andNoOrderIsIssued() throws Exception {
        UUID cartId = cartWithOneTicket(buyer);
        JsonNode started = json(start(buyer, cartId, UUID.randomUUID()).andExpect(status().isAccepted()));
        String intentId = jdbc.queryForObject("select provider_intent_id from pending_checkouts where id = ?", String.class,
                UUID.fromString(started.get("checkoutId").asText()));

        webhook(webhookBody("payment.paid", intentId, "pay_short", started.get("amount").asLong() - 1, "PHP"), true).andExpect(status().isOk());

        assertThat(ordersOfBuyer()).isZero();
        verify(gateway).refund(eq("pay_short"), any());
        assertThat(jdbc.queryForObject("select status from pending_checkouts where provider_intent_id = ?", String.class, intentId)).isEqualTo("REFUNDED");
    }

    @Test
    void aFailedPayment_leavesTheCartAlone_andFreesTheBuyerToTryAgain() throws Exception {
        UUID cartId = cartWithOneTicket(buyer);
        UUID key = UUID.randomUUID();
        JsonNode started = json(start(buyer, cartId, key).andExpect(status().isAccepted()));
        String intentId = jdbc.queryForObject("select provider_intent_id from pending_checkouts where id = ?", String.class,
                UUID.fromString(started.get("checkoutId").asText()));
        doReturn(new PaymentGatewayClient.PaymentIntentInfo("pi_it_retry_" + UUID.randomUUID().toString().replace("-", ""), "client_key_2"))
                .when(gateway).createPaymentIntent(any(), any(), any());

        webhook(webhookBody("payment.failed", intentId, "pay_failed", started.get("amount").asLong(), "PHP"), true).andExpect(status().isOk());

        assertThat(ordersOfBuyer()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from cart_items where cart_id = ?", Integer.class, cartId)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/checkouts/{id}", UUID.fromString(started.get("checkoutId").asText())).header("Authorization", buyer))
                .andExpect(jsonPath("$.status").value("FAILED"));
        // the same key starts a fresh checkout now
        start(buyer, cartId, key).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.clientKey").value("client_key_2"));
    }

    @Test
    void repeatingTheStartWithTheSameKey_returnsTheSameCheckout() throws Exception {
        UUID cartId = cartWithOneTicket(buyer);
        UUID key = UUID.randomUUID();

        JsonNode first = json(start(buyer, cartId, key).andExpect(status().isAccepted()));
        JsonNode again = json(start(buyer, cartId, key).andExpect(status().isAccepted()));

        assertThat(again.get("checkoutId").asText()).isEqualTo(first.get("checkoutId").asText());
        verify(gateway).createPaymentIntent(any(), any(), any());   // only one intent was ever created
    }

    @Test
    void anotherBuyer_cannotStartFromMyCart_norSeeMyCheckout() throws Exception {
        UUID cartId = cartWithOneTicket(buyer);
        JsonNode started = json(start(buyer, cartId, UUID.randomUUID()).andExpect(status().isAccepted()));
        String stranger = token(UUID.randomUUID());

        start(stranger, cartId, UUID.randomUUID()).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/checkouts/{id}", UUID.fromString(started.get("checkoutId").asText())).header("Authorization", stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/checkouts/{id}", UUID.fromString(started.get("checkoutId").asText()))).andExpect(status().isUnauthorized());
    }
}
