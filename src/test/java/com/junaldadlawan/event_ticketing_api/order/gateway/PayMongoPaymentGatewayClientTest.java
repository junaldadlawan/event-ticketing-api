package com.junaldadlawan.event_ticketing_api.order.gateway;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** PayMongo against a stub server: what is sent (JSON, centavos, PHP, Basic auth) and how every outcome maps. */
class PayMongoPaymentGatewayClientTest {

    private static final String BASIC = "Basic " + Base64.getEncoder().encodeToString("sk_test_not_a_real_key:".getBytes(StandardCharsets.UTF_8));

    private MockRestServiceServer server;
    private PayMongoPaymentGatewayClient client;
    private final Money fiftyPesos = Money.builder().amount(5000).currency("PHP").build();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://paymongo.test/v1").defaultHeader(HttpHeaders.AUTHORIZATION, BASIC);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PayMongoPaymentGatewayClient(builder.build());
    }

    private void respond(String path, HttpStatus status, String json) {
        server.expect(requestTo("https://paymongo.test/v1" + path)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(json));
    }

    @Test
    void confirmsByWebhook() {
        assertThat(client.confirmsByWebhook()).isTrue();
    }

    @Test
    void createPaymentIntent_sendsCentavosInPhp_forACardOnly_andReturnsTheIdAndClientKey() {
        server.expect(requestTo("https://paymongo.test/v1/payment_intents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BASIC))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(jsonPath("$.data.attributes.amount").value(5000))
                .andExpect(jsonPath("$.data.attributes.currency").value("PHP"))
                .andExpect(jsonPath("$.data.attributes.payment_method_allowed[0]").value("card"))
                .andExpect(jsonPath("$.data.attributes.capture_type").value("automatic"))
                .andExpect(jsonPath("$.data.attributes.metadata.reference").value("checkout-abc"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"pi_123\",\"attributes\":{\"client_key\":\"pi_123_client_xyz\",\"status\":\"awaiting_payment_method\"}}}",
                        MediaType.APPLICATION_JSON));

        PaymentGatewayClient.PaymentIntentInfo intent = client.createPaymentIntent(fiftyPesos, "Event ticket order", "checkout-abc");

        assertThat(intent.intentId()).isEqualTo("pi_123");
        assertThat(intent.clientKey()).isEqualTo("pi_123_client_xyz");
        server.verify();
    }

    @Test
    void createPaymentIntent_toSaveTheCard_asksPayMongoToVaultItOnTheCustomer() {
        server.expect(requestTo("https://paymongo.test/v1/payment_intents")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.data.attributes.setup_future_usage.session_type").value("on_session"))
                .andExpect(jsonPath("$.data.attributes.setup_future_usage.customer_id").value("cus_1"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"pi_9\",\"attributes\":{\"client_key\":\"pi_9_client_k\"}}}", MediaType.APPLICATION_JSON));

        assertThat(client.createPaymentIntent(fiftyPesos, "Event ticket order", "checkout-abc", "cus_1").intentId()).isEqualTo("pi_9");
        server.verify();
    }

    @Test
    void createPaymentIntent_toSave_aSetupFutureUsageRefusal_isTold_apartFromAnyOther400_or5xx() {
        respond("/payment_intents", HttpStatus.BAD_REQUEST, "{\"errors\":[{\"detail\":\"On session payments are not yet supported.\"}]}");
        assertThatThrownBy(() -> client.createPaymentIntent(fiftyPesos, "x", "checkout-abc", "cus_1")).isInstanceOf(CardSavingUnavailableException.class);

        server.reset();
        respond("/payment_intents", HttpStatus.BAD_REQUEST, "{\"errors\":[{\"detail\":\"amount is invalid\"}]}");
        assertThatThrownBy(() -> client.createPaymentIntent(fiftyPesos, "x", "checkout-abc", "cus_1"))
                .isInstanceOf(BadRequestException.class).isNotInstanceOf(CardSavingUnavailableException.class);

        server.reset();
        respond("/payment_intents", HttpStatus.INTERNAL_SERVER_ERROR, "{}");
        assertThatThrownBy(() -> client.createPaymentIntent(fiftyPesos, "x", "checkout-abc", "cus_1")).isNotInstanceOf(BadRequestException.class);

        server.reset();   // the same refusal without a customer to save on is a plain rejection
        respond("/payment_intents", HttpStatus.BAD_REQUEST, "{\"errors\":[{\"detail\":\"On session payments are not yet supported.\"}]}");
        assertThatThrownBy(() -> client.createPaymentIntent(fiftyPesos, "x", "checkout-abc")).isNotInstanceOf(CardSavingUnavailableException.class);
    }

    @Test
    void createCustomer_sendsTheFieldsPayMongoRequires() {
        server.expect(requestTo("https://paymongo.test/v1/customers")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.data.attributes.first_name").value("Jamie"))
                .andExpect(jsonPath("$.data.attributes.last_name").value("Cruz"))
                .andExpect(jsonPath("$.data.attributes.email").value("j@test.local"))
                .andExpect(jsonPath("$.data.attributes.default_device").value("email"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"cus_7\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.createCustomer("j@test.local", "Jamie", "Cruz")).isEqualTo("cus_7");
        server.verify();
    }

    @Test
    void attachSavedCard_setsTheSecurityCodeThenAttaches_andAnswersWithTheBanksRedirect() {
        server.expect(requestTo("https://paymongo.test/v1/payment_methods/pm_1")).andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.data.attributes.details.cvc").value("123"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"pm_1\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://paymongo.test/v1/payment_intents/pi_9/attach")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.data.attributes.payment_method").value("pm_1"))
                .andExpect(jsonPath("$.data.attributes.client_key").value("pi_9_client_k"))
                .andExpect(jsonPath("$.data.attributes.return_url").value("https://app.test/back"))
                .andRespond(withSuccess("{\"data\":{\"attributes\":{\"status\":\"awaiting_next_action\",\"next_action\":{\"type\":\"redirect\","
                        + "\"redirect\":{\"url\":\"https://bank.test/3ds\"}}}}}", MediaType.APPLICATION_JSON));

        PaymentGatewayClient.AttachOutcome outcome = client.attachSavedCard("pi_9", "pi_9_client_k", "pm_1", "123", "https://app.test/back");

        assertThat(outcome.redirectUrl()).isEqualTo("https://bank.test/3ds");
        assertThat(outcome.failureReason()).isNull();
        server.verify();
    }

    @Test
    void attachSavedCard_aDeclinedCard_isAFailureReason_notAnException() {
        server.expect(requestTo("https://paymongo.test/v1/payment_intents/pi_9/attach")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":{\"attributes\":{\"status\":\"awaiting_payment_method\","
                        + "\"last_payment_error\":{\"failed_message\":\"Insufficient funds\"}}}}", MediaType.APPLICATION_JSON));

        PaymentGatewayClient.AttachOutcome outcome = client.attachSavedCard("pi_9", "k", "pm_1", null, "https://app.test/back");

        assertThat(outcome.failureReason()).isEqualTo("Insufficient funds");
        server.verify();   // no security code: only the attach call
    }

    @Test
    void customerCards_readsTheVaultedPaymentMethods() {
        server.expect(requestTo("https://paymongo.test/v1/customers/cus_1/payment_methods")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"pm_1\",\"attributes\":{\"details\":{\"last4\":\"4345\",\"exp_month\":12,\"exp_year\":2030,"
                        + "\"card_brand\":\"visa\"}}}]}", MediaType.APPLICATION_JSON));

        assertThat(client.customerCards("cus_1")).containsExactly(new PaymentGatewayClient.VaultedCard("pm_1", "visa", "4345", 12, 2030));
        server.verify();
    }

    @Test
    void createPaymentIntent_aCurrencyOtherThanPhp_isRefusedWithoutCallingPayMongo() {
        assertThatThrownBy(() -> client.createPaymentIntent(Money.builder().amount(5000).currency("USD").build(), "x", "checkout-abc"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("PHP");
        server.verify();
    }

    @Test
    void createPaymentIntent_aRejectedRequest_isABadRequest_andAnOutage_isABadGateway() {
        respond("/payment_intents", HttpStatus.BAD_REQUEST, "{\"errors\":[{\"code\":\"parameter_invalid\",\"detail\":\"amount is too small\"}]}");
        respond("/payment_intents", HttpStatus.INTERNAL_SERVER_ERROR, "{}");

        assertThatThrownBy(() -> client.createPaymentIntent(fiftyPesos, "x", "r")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("amount is too small");
        assertThatThrownBy(() -> client.createPaymentIntent(fiftyPesos, "x", "r")).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void charge_withAToken_isAFailedPayment_becauseTheCardIsEnteredInTheBrowser() {
        PaymentResult result = client.charge("tok_anything", fiftyPesos);

        assertThat(result.successful()).isFalse();
        server.verify();   // nothing was sent
    }

    @Test
    void refund_refundsThePaymentById_forTheAmountInCentavos() {
        server.expect(requestTo("https://paymongo.test/v1/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.data.attributes.payment_id").value("pay_123"))
                .andExpect(jsonPath("$.data.attributes.amount").value(2000))
                .andExpect(jsonPath("$.data.attributes.reason").value("others"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"ref_1\",\"attributes\":{\"status\":\"pending\"}}}", MediaType.APPLICATION_JSON));

        PaymentResult result = client.refund("pay_123", Money.builder().amount(2000).currency("PHP").build());

        assertThat(result.successful()).isTrue();
        assertThat(result.gatewayRef()).isEqualTo("ref_1");
        server.verify();
    }

    @Test
    void refund_ofAPaymentNotMadeThroughPayMongo_failsWithoutCallingPayMongo() {
        for (String ref : new String[] {"mock_6f1c", "pi_3Stripe", "pi_paymongo_intent", null}) {
            PaymentResult result = client.refund(ref, fiftyPesos);
            assertThat(result.successful()).isFalse();
            assertThat(result.failureReason()).contains("not made through PayMongo");
        }
        server.verify();
    }

    @Test
    void refund_payMongoRefusing_isAFailedRefund_withItsDetail() {
        respond("/refunds", HttpStatus.BAD_REQUEST, "{\"errors\":[{\"code\":\"refund_amount_exceeds_payment\",\"detail\":\"The refund amount exceeds the payment.\"}]}");

        PaymentResult result = client.refund("pay_123", fiftyPesos);

        assertThat(result.successful()).isFalse();
        assertThat(result.failureReason()).isEqualTo("The refund amount exceeds the payment.");
    }

    @Test
    void refund_aFailedRefundStatus_isAFailure_andAnOutage_throws() {
        respond("/refunds", HttpStatus.OK, "{\"data\":{\"id\":\"ref_2\",\"attributes\":{\"status\":\"failed\"}}}");
        respond("/refunds", HttpStatus.BAD_GATEWAY, "{}");

        assertThat(client.refund("pay_123", fiftyPesos).successful()).isFalse();
        assertThatThrownBy(() -> client.refund("pay_123", fiftyPesos)).isInstanceOf(ApiException.class);
    }

    @Test
    void theApplicationRefusesToStartInPayMongoModeWithoutAKey() {
        PayMongoClientConfig config = new PayMongoClientConfig();
        assertThatThrownBy(() -> config.paymongoRestClient("", "https://paymongo.test/v1")).isInstanceOf(IllegalStateException.class);
        assertThat(config.paymongoRestClient("sk_test_not_a_real_key", "https://paymongo.test/v1")).isNotNull();
    }
}
