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
