package com.junaldadlawan.event_ticketing_api.order;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.gateway.PayMongoPaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.order.gateway.PayMongoWebhookVerifier;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saving a card with a payment and paying with it later, on PayMongo (the provider's calls are stubbed; the webhook goes through the real
 * security chain). The card is vaulted when "save this card" was ticked and the payment succeeded; a saved card is then attached by the server.
 */
@SpringBootTest(properties = {"payments.gateway=paymongo", "payments.paymongo.secret-key=sk_test_not_a_real_key",
        "payments.paymongo.webhook-secret=whsk_test_secret"})
@AutoConfigureMockMvc
class PayMongoSavedCardIntegrationTest {

    private static final String RETURN_URL = "https://app.test/checkout/return";

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
    private JdbcTemplate jdbc;
    @Autowired
    private com.junaldadlawan.event_ticketing_api.order.service.CardVaultingStatus cardVaultingStatus;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> cartIds = new ArrayList<>();
    private final List<UUID> keyIds = new ArrayList<>();
    private UUID orgId;
    private UUID eventId;
    private UUID ticketTypeId;
    private User me;
    private String token;

    @BeforeEach
    void setUp() {
        cardVaultingStatus.reset();   // what an earlier test learned about vaulting must not leak
        orgId = organizationRepository.save(Organization.builder().name("Saved Card Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(UUID.randomUUID()).documents(List.of()).build()).getId();
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        eventId = eventRepository.save(Event.builder().organizationId(orgId).title("Saved Card Event").description("d").category("music")
                .status(EventStatus.ON_SALE).ticketPrefix("C" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
        ticketTypeId = ticketTypeRepository.save(TicketType.builder().eventId(eventId).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(100_00).currency("PHP").build()).quantityTotal(50).quantityAvailable(50)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS)).maxPerOrder(10).build()).getId();
        me = user();
        token = token(me);

        doAnswer(call -> new PaymentGatewayClient.PaymentIntentInfo("pi_it_" + UUID.randomUUID().toString().replace("-", ""), "client_key_secret"))
                .when(gateway).createPaymentIntent(any(), any(), any());
        doAnswer(call -> new PaymentGatewayClient.PaymentIntentInfo("pi_it_" + UUID.randomUUID().toString().replace("-", ""), "client_key_secret"))
                .when(gateway).createPaymentIntent(any(), any(), any(), any());
        doReturn("cus_it_1").when(gateway).createCustomer(any(), any(), any());
        doReturn(PaymentGatewayClient.AttachOutcome.class.cast(new PaymentGatewayClient.AttachOutcome("succeeded", null, null)))
                .when(gateway).attachSavedCard(any(), any(), any(), any(), any());
    }

    @AfterEach
    void tearDown() {
        for (UUID id : userIds) {
            for (UUID orderId : jdbc.queryForList("select id from orders where buyer_id = ?", UUID.class, id)) {
                jdbc.update("delete from tickets where order_id = ?", orderId);
                jdbc.update("delete from payments where order_id = ?", orderId);
                jdbc.update("delete from orders where id = ?", orderId);
            }
            jdbc.update("delete from pending_checkouts where buyer_id = ?", id);
            jdbc.update("delete from payment_methods where user_id = ?", id);
        }
        keyIds.forEach(id -> jdbc.update("delete from checkout_idempotency_keys where id = ?", id));
        cartIds.forEach(id -> {
            jdbc.update("delete from cart_items where cart_id = ?", id);
            jdbc.update("delete from carts where id = ?", id);
        });
        userIds.forEach(userRepository::deleteById);
        jdbc.update("delete from ticket_types where event_id = ?", eventId);
        eventRepository.deleteById(eventId);
        organizationRepository.deleteById(orgId);
    }

    // ---- fixtures ----

    private User user() {
        User saved = userRepository.save(User.builder().firstName("Card").lastName("Holder")
                .email("saved-card-" + UUID.randomUUID() + "@test.local").passwordHash("x").role(Role.CUSTOMER).build());
        userIds.add(saved.getId());
        return saved;
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private UUID cartWithOneTicket(String caller) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/carts").header("Authorization", caller)).andExpect(status().isCreated()).andReturn();
        UUID cartId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        cartIds.add(cartId);
        mockMvc.perform(post("/api/v1/carts/{id}/items", cartId).header("Authorization", caller).contentType("application/json")
                .content("{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":1}")).andExpect(status().isCreated());
        return cartId;
    }

    private ResultActions checkout(String caller, UUID cartId, UUID key, String body) throws Exception {
        keyIds.add(key);
        return mockMvc.perform(post("/api/v1/carts/{id}/checkout", cartId).header("Authorization", caller)
                .header("Idempotency-Key", key.toString()).contentType("application/json").content(body));
    }

    private String savedCardBody(UUID cardId) {
        return "{\"paymentMethodId\":\"" + cardId + "\",\"securityCode\":\"123\",\"returnUrl\":\"" + RETURN_URL + "\"}";
    }

    private void paid(JsonNode started, String paymentId) throws Exception {
        String intentId = jdbc.queryForObject("select provider_intent_id from pending_checkouts where id = ?", String.class,
                UUID.fromString(started.get("checkoutId").asText()));
        String body = "{\"data\":{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"event\",\"attributes\":{\"type\":\"payment.paid\",\"livemode\":false,"
                + "\"data\":{\"id\":\"" + paymentId + "\",\"type\":\"payment\",\"attributes\":{\"amount\":" + started.get("amount").asLong()
                + ",\"currency\":\"PHP\",\"payment_intent_id\":\"" + intentId + "\",\"status\":\"paid\"}}}}}";
        String timestamp = "1700000000";
        mockMvc.perform(post("/api/v1/webhooks/paymongo").contentType("application/json")
                .header("Paymongo-Signature", "t=" + timestamp + ",te=" + verifier.sign(timestamp + "." + body) + ",li=")
                .content(body.getBytes(StandardCharsets.UTF_8))).andExpect(status().isOk());
    }

    private int orders(UUID userId) {
        return jdbc.queryForObject("select count(*) from orders where buyer_id = ?", Integer.class, userId);
    }

    private UUID oldCard(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into payment_methods (id, user_id, holder_name, brand, last4, exp_month, exp_year, is_primary, created_at, updated_at, created_by) "
                + "values (?, ?, 'Card Holder', 'visa', '4242', 12, 2099, false, now(), now(), 'it')", id, userId);
        return id;
    }

    // ---- tests ----

    @Test
    void saveACardWithAPayment_thenPayWithItWithoutTypingItAgain() throws Exception {
        // 1. a new card, typed in the browser, with "save this card"
        UUID first = cartWithOneTicket(token);
        JsonNode started = json(checkout(token, first, UUID.randomUUID(), "{\"saveCard\":true}").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.clientKey").value("client_key_secret")).andExpect(jsonPath("$.nextAction").doesNotExist()));
        verify(gateway).createPaymentIntent(any(), any(), any(), eq("cus_it_1"));   // vaulted on the user's PayMongo customer
        doReturn(List.of(new PaymentGatewayClient.VaultedCard("pm_it_saved", "visa", "4345", 12, 2099))).when(gateway).customerCards("cus_it_1");
        paid(started, "pay_it_first");

        assertThat(orders(me.getId())).isEqualTo(1);
        mockMvc.perform(get("/api/v1/users/me/payment-methods").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].last4").value("4345"))
                .andExpect(jsonPath("$[0].canChargeAgain").value(true))
                .andExpect(jsonPath("$[0].paymongoPaymentMethodId").doesNotExist());   // never shown
        assertThat(jdbc.queryForObject("select paymongo_customer_id from users where id = ?", String.class, me.getId())).isEqualTo("cus_it_1");
        UUID cardId = jdbc.queryForObject("select id from payment_methods where user_id = ?", UUID.class, me.getId());

        // 2. the next purchase with the saved card: the server starts the payment and attaches the card, the webhook completes the order
        UUID second = cartWithOneTicket(token);
        JsonNode again = json(checkout(token, second, UUID.randomUUID(), savedCardBody(cardId)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.nextAction").value("NONE")).andExpect(jsonPath("$.redirectUrl").doesNotExist()));
        verify(gateway).attachSavedCard(any(), eq("client_key_secret"), eq("pm_it_saved"), eq("123"), eq(RETURN_URL));
        paid(again, "pay_it_second");

        assertThat(orders(me.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from payment_methods where user_id = ?", Integer.class, me.getId())).isEqualTo(1);   // not saved twice
    }

    @Test
    void anotherUsersCardIsA404_andNothingIsStarted() throws Exception {
        User other = user();
        UUID theirCard = oldCard(other.getId());
        UUID cartId = cartWithOneTicket(token);

        checkout(token, cartId, UUID.randomUUID(), savedCardBody(theirCard)).andExpect(status().isNotFound());

        verify(gateway, never()).createPaymentIntent(any(), any(), any());
        verify(gateway, never()).attachSavedCard(any(), any(), any(), any(), any());
        assertThat(jdbc.queryForObject("select count(*) from pending_checkouts where cart_id = ?", Integer.class, cartId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from cart_items where cart_id = ?", Integer.class, cartId)).isEqualTo(1);
    }

    @Test
    void aCardSavedBeforeThisChange_cannotBeChargedAgain_andCheckoutAsksToReEnterIt() throws Exception {
        UUID old = oldCard(me.getId());
        UUID cartId = cartWithOneTicket(token);

        mockMvc.perform(get("/api/v1/users/me/payment-methods").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].canChargeAgain").value(false));
        checkout(token, cartId, UUID.randomUUID(), savedCardBody(old)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Re-enter this card to use it again"));

        verify(gateway, never()).createPaymentIntent(any(), any(), any());
        assertThat(jdbc.queryForObject("select count(*) from pending_checkouts where cart_id = ?", Integer.class, cartId)).isZero();
    }

    @Test
    void whenTheBankAsksFor3DSecure_theResponseCarriesTheRedirect_andARepeatDoesNotAttachAgain() throws Exception {
        UUID cardId = vaulted();
        UUID cartId = cartWithOneTicket(token);
        doReturn(new PaymentGatewayClient.AttachOutcome("awaiting_next_action", "https://bank.test/3ds/abc", null))
                .when(gateway).attachSavedCard(any(), any(), any(), any(), any());
        UUID key = UUID.randomUUID();

        JsonNode started = json(checkout(token, cartId, key, savedCardBody(cardId)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.nextAction").value("REDIRECT")).andExpect(jsonPath("$.redirectUrl").value("https://bank.test/3ds/abc")));
        JsonNode repeat = json(checkout(token, cartId, key, savedCardBody(cardId)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.nextAction").value("REDIRECT")));

        assertThat(repeat.get("checkoutId").asText()).isEqualTo(started.get("checkoutId").asText());
        verify(gateway, times(1)).attachSavedCard(any(), any(), any(), any(), any());
        paid(started, "pay_it_3ds");   // the buyer authorised: the webhook issues the order
        assertThat(orders(me.getId())).isEqualTo(1);
    }

    @Test
    void aDeclinedSavedCard_isA402_andLeavesTheCartIntactForAnotherTry() throws Exception {
        UUID cardId = vaulted();
        UUID cartId = cartWithOneTicket(token);
        doReturn(new PaymentGatewayClient.AttachOutcome("awaiting_payment_method", null, "Your card has insufficient funds."))
                .when(gateway).attachSavedCard(any(), any(), any(), any(), any());
        UUID key = UUID.randomUUID();

        checkout(token, cartId, key, savedCardBody(cardId)).andExpect(status().isPaymentRequired());

        assertThat(orders(me.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from cart_items where cart_id = ?", Integer.class, cartId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from pending_checkouts where cart_id = ?", String.class, cartId)).isEqualTo("FAILED");
        // the same key is free again: a second try with a working card goes through
        doReturn(new PaymentGatewayClient.AttachOutcome("succeeded", null, null)).when(gateway).attachSavedCard(any(), any(), any(), any(), any());
        checkout(token, cartId, key, savedCardBody(cardId)).andExpect(status().isAccepted()).andExpect(jsonPath("$.nextAction").value("NONE"));
    }

    @Test
    void aSavedCardNeedsItsSecurityCodeAndAReturnAddress() throws Exception {
        UUID cardId = vaulted();
        UUID cartId = cartWithOneTicket(token);

        checkout(token, cartId, UUID.randomUUID(), "{\"paymentMethodId\":\"" + cardId + "\",\"returnUrl\":\"" + RETURN_URL + "\"}").andExpect(status().isBadRequest());
        checkout(token, cartId, UUID.randomUUID(), "{\"paymentMethodId\":\"" + cardId + "\",\"securityCode\":\"123\"}").andExpect(status().isBadRequest());
        checkout(token, cartId, UUID.randomUUID(), "{\"paymentMethodId\":\"" + cardId + "\",\"securityCode\":\"12a\",\"returnUrl\":\"" + RETURN_URL + "\"}")
                .andExpect(status().isBadRequest());
        verify(gateway, never()).createPaymentIntent(any(), any(), any());
    }

    /** Pays a cart with "save this card" while PayMongo lists exactly these vaulted cards for the customer. */
    private void paySavingCard(List<PaymentGatewayClient.VaultedCard> vaultedByPayMongo) throws Exception {
        UUID cartId = cartWithOneTicket(token);
        JsonNode started = json(checkout(token, cartId, UUID.randomUUID(), "{\"saveCard\":true}").andExpect(status().isAccepted()));
        doReturn(vaultedByPayMongo).when(gateway).customerCards("cus_it_1");
        paid(started, "pay_it_" + UUID.randomUUID());
    }

    private int liveCards() {
        return jdbc.queryForObject("select count(*) from payment_methods where user_id = ? and deleted_at is null", Integer.class, me.getId());
    }

    @Test
    void aCardTheUserRemoved_isRestoredByANewSave_andBecomesPrimaryWhenItIsTheOnlyOne() throws Exception {
        UUID card = vaulted();
        jdbc.update("update payment_methods set deleted_at = now(), is_primary = false where id = ?", card);

        paySavingCard(List.of(new PaymentGatewayClient.VaultedCard("pm_it_vaulted", "visa", "4242", 12, 2099)));

        assertThat(orders(me.getId())).isEqualTo(1);
        assertThat(liveCards()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select deleted_at is null and is_primary from payment_methods where id = ?", Boolean.class, card)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from payment_methods where user_id = ?", Integer.class, me.getId())).isEqualTo(1);   // the same row
    }

    @Test
    void aRemovedCardStaysRemoved_whenADifferentCardIsSaved() throws Exception {
        UUID removed = vaulted();
        jdbc.update("update payment_methods set deleted_at = now(), is_primary = false where id = ?", removed);

        paySavingCard(List.of(new PaymentGatewayClient.VaultedCard("pm_it_vaulted", "visa", "4242", 12, 2099),
                new PaymentGatewayClient.VaultedCard("pm_it_other", "mastercard", "0002", 1, 2099)));

        assertThat(liveCards()).isEqualTo(1);   // only the new one
        assertThat(jdbc.queryForObject("select deleted_at is not null from payment_methods where id = ?", Boolean.class, removed)).isTrue();
        assertThat(jdbc.queryForObject("select last4 from payment_methods where user_id = ? and deleted_at is null", String.class, me.getId())).isEqualTo("0002");
    }

    @Test
    void anOldCardWithTheSameLast4AndExpiry_isUpgradedToChargeable_notDuplicated() throws Exception {
        UUID old = oldCard(me.getId());   // visa ...4242 12/2099, no payment method id

        paySavingCard(List.of(new PaymentGatewayClient.VaultedCard("pm_it_new", "visa", "4242", 12, 2099)));

        assertThat(jdbc.queryForObject("select count(*) from payment_methods where user_id = ?", Integer.class, me.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select paymongo_payment_method_id from payment_methods where id = ?", String.class, old)).isEqualTo("pm_it_new");
        mockMvc.perform(get("/api/v1/users/me/payment-methods").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].canChargeAgain").value(true));
    }

    @Test
    void whenPayMongoReturnsNoCards_theOrderStandsAndNothingIsSaved() throws Exception {
        paySavingCard(List.of());

        assertThat(orders(me.getId())).isEqualTo(1);
        assertThat(liveCards()).isZero();
        assertThat(jdbc.queryForObject("select status from pending_checkouts where buyer_id = ?", String.class, me.getId())).isEqualTo("PAID");
    }

    @Test
    void ifTheCustomerCannotBeCreated_thePaymentStillStarts_andTheCardIsJustNotSaved() throws Exception {
        doThrow(new com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException("rejected the customer")).when(gateway).createCustomer(any(), any(), any());
        UUID cartId = cartWithOneTicket(token);

        JsonNode started = json(checkout(token, cartId, UUID.randomUUID(), "{\"saveCard\":true}").andExpect(status().isAccepted()));

        verify(gateway, never()).createPaymentIntent(any(), any(), any(), any());   // a plain intent: nothing to vault
        assertThat(jdbc.queryForObject("select save_card from pending_checkouts where id = ?", Boolean.class, UUID.fromString(started.get("checkoutId").asText()))).isFalse();
        paid(started, "pay_it_nosave");
        assertThat(orders(me.getId())).isEqualTo(1);
    }

    @Test
    void ifPayMongoRefusesToSaveTheCard_thePaymentStillStartsWithoutSaving_andTheResponseSaysSo() throws Exception {
        doThrow(new com.junaldadlawan.event_ticketing_api.order.gateway.CardSavingUnavailableException(
                "The payment provider rejected the request: On session payments are not yet supported.")).when(gateway).createPaymentIntent(any(), any(), any(), any());
        UUID cartId = cartWithOneTicket(token);

        JsonNode started = json(checkout(token, cartId, UUID.randomUUID(), "{\"saveCard\":true}").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.clientKey").value("client_key_secret")).andExpect(jsonPath("$.cardSaving").value("UNAVAILABLE")));

        verify(gateway).createPaymentIntent(any(), any(), any());   // the plain intent, without setup_future_usage
        assertThat(jdbc.queryForObject("select save_card from pending_checkouts where id = ?", Boolean.class, UUID.fromString(started.get("checkoutId").asText()))).isFalse();
        paid(started, "pay_it_unavailable");
        assertThat(orders(me.getId())).isEqualTo(1);
        assertThat(liveCards()).isZero();

        // remembered for a few minutes: the next checkout does not ask PayMongo to vault again
        checkout(token, cartWithOneTicket(token), UUID.randomUUID(), "{\"saveCard\":true}").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.cardSaving").value("UNAVAILABLE"));
        verify(gateway, times(1)).createPaymentIntent(any(), any(), any(), any());
    }

    @Test
    void anyOtherRejection_stillFailsTheCheckout_asBefore() throws Exception {
        doThrow(new com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException("The payment provider rejected the request: invalid amount"))
                .when(gateway).createPaymentIntent(any(), any(), any(), any());
        UUID cartId = cartWithOneTicket(token);

        checkout(token, cartId, UUID.randomUUID(), "{\"saveCard\":true}").andExpect(status().isBadRequest());
        verify(gateway, never()).createPaymentIntent(any(), any(), any());   // no second try

        doThrow(new com.junaldadlawan.event_ticketing_api.common.exception.ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "The payment provider is unavailable"))
                .when(gateway).createPaymentIntent(any(), any(), any(), any());
        checkout(token, cartId, UUID.randomUUID(), "{\"saveCard\":true}").andExpect(status().isBadGateway());
        verify(gateway, never()).createPaymentIntent(any(), any(), any());
    }

    @Test
    void aCheckoutThatDoesNotAskToSave_saysNotRequested() throws Exception {
        checkout(token, cartWithOneTicket(token), UUID.randomUUID(), "{}").andExpect(status().isAccepted()).andExpect(jsonPath("$.cardSaving").value("NOT_REQUESTED"));
    }

    private UUID vaulted() {
        UUID id = oldCard(me.getId());
        jdbc.update("update payment_methods set paymongo_payment_method_id = 'pm_it_vaulted' where id = ?", id);
        return id;
    }
}
