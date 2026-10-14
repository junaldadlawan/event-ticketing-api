package com.junaldadlawan.event_ticketing_api.order.gateway;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Real charges and refunds against a stub Stripe: what is sent and how every outcome maps to a PaymentResult. */
class StripePaymentGatewayClientTest {

    private MockRestServiceServer server;
    private StripePaymentGatewayClient client;
    private final Money fiftyPesos = Money.builder().amount(5000).currency("PHP").build();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://stripe.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer sk_test_not_a_real_key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new StripePaymentGatewayClient(builder.build());
    }

    private void respond(String path, HttpStatus status, String json) {
        server.expect(requestTo("https://stripe.test" + path)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(json));
    }

    @Test
    void charge_createsAndConfirmsAPaymentIntent_inTheOrdersCurrency_andReturnsItsId() {
        server.expect(requestTo("https://stripe.test/v1/payment_intents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "checkout-abc"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk_test_not_a_real_key"))
                .andExpect(content().string(containsString("amount=5000")))
                .andExpect(content().string(containsString("currency=php")))
                .andExpect(content().string(containsString("payment_method=pm_card")))
                .andExpect(content().string(containsString("customer=cus_1")))
                .andExpect(content().string(containsString("confirm=true")))
                .andExpect(content().string(containsString("allow_redirects%5D=never")))
                .andRespond(withSuccess("{\"id\":\"pi_123\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));

        PaymentResult result = client.charge("pm_card", "cus_1", fiftyPesos, "checkout-abc");

        assertThat(result.successful()).isTrue();
        assertThat(result.gatewayRef()).isEqualTo("pi_123");
        server.verify();
    }

    @Test
    void charge_aOneOffToken_isSentWithoutACustomer() {
        server.expect(requestTo("https://stripe.test/v1/payment_intents"))
                .andExpect(content().string(not(containsString("customer="))))
                .andRespond(withSuccess("{\"id\":\"pi_9\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));

        assertThat(client.charge("pm_card", fiftyPesos).successful()).isTrue();
        server.verify();
    }

    @Test
    void charge_aTokenThatIsNotAStripePaymentMethod_failsWithoutCallingStripe() {
        PaymentResult result = client.charge("tok_ok", fiftyPesos);

        assertThat(result.successful()).isFalse();
        server.verify();   // no request was expected, none was made
    }

    @Test
    void charge_aDeclinedCard_isAFailedPayment_withStripesMessage_notAnException() {
        respond("/v1/payment_intents", HttpStatus.PAYMENT_REQUIRED,
                "{\"error\":{\"type\":\"card_error\",\"code\":\"card_declined\",\"message\":\"Your card was declined.\"}}");

        PaymentResult result = client.charge("pm_card", "cus_1", fiftyPesos, "checkout-abc");

        assertThat(result.successful()).isFalse();
        assertThat(result.failureReason()).isEqualTo("Your card was declined.");
        assertThat(result.gatewayRef()).isNull();
    }

    @Test
    void charge_anInvalidRequest_isAlsoAFailedPayment() {
        respond("/v1/payment_intents", HttpStatus.BAD_REQUEST, "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"No such PaymentMethod\"}}");

        PaymentResult result = client.charge("pm_missing", fiftyPesos);

        assertThat(result.successful()).isFalse();
        assertThat(result.failureReason()).contains("No such PaymentMethod");
    }

    @Test
    void charge_aCardNeeding3DSecure_isCancelledAndFailed() {
        respond("/v1/payment_intents", HttpStatus.OK, "{\"id\":\"pi_3ds\",\"status\":\"requires_action\"}");
        respond("/v1/payment_intents/pi_3ds/cancel", HttpStatus.OK, "{\"id\":\"pi_3ds\",\"status\":\"canceled\"}");

        PaymentResult result = client.charge("pm_card", "cus_1", fiftyPesos, "checkout-abc");

        assertThat(result.successful()).isFalse();
        assertThat(result.failureReason()).contains("3-D Secure");
        server.verify();   // the unfinished intent was cancelled
    }

    @Test
    void charge_stripeBeingDown_throwsABadGateway_soTheCheckoutKeyIsFreedForARetry() {
        respond("/v1/payment_intents", HttpStatus.INTERNAL_SERVER_ERROR, "{}");

        assertThatThrownBy(() -> client.charge("pm_card", "cus_1", fiftyPesos, "checkout-abc"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void refund_refundsThePaymentIntent_forTheAmount() {
        server.expect(requestTo("https://stripe.test/v1/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("payment_intent=pi_123")))
                .andExpect(content().string(containsString("amount=2000")))
                .andRespond(withSuccess("{\"id\":\"re_1\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));

        PaymentResult result = client.refund("pi_123", Money.builder().amount(2000).currency("PHP").build());

        assertThat(result.successful()).isTrue();
        assertThat(result.gatewayRef()).isEqualTo("re_1");
        server.verify();
    }

    @Test
    void refund_ofAPaymentNotMadeThroughStripe_failsWithoutCallingStripe() {
        PaymentResult result = client.refund("mock_6f1c", fiftyPesos);

        assertThat(result.successful()).isFalse();
        assertThat(result.failureReason()).contains("not made through Stripe");
        server.verify();
    }

    @Test
    void refund_stripeRefusing_isAFailedRefund() {
        respond("/v1/refunds", HttpStatus.BAD_REQUEST, "{\"error\":{\"message\":\"Charge pi_123 has already been refunded.\"}}");

        PaymentResult result = client.refund("pi_123", fiftyPesos);

        assertThat(result.successful()).isFalse();
        assertThat(result.failureReason()).contains("already been refunded");
    }
}
