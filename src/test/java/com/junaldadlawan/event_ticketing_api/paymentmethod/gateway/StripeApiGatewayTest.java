package com.junaldadlawan.event_ticketing_api.paymentmethod.gateway;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The real Stripe client against a stub server: what is sent (form fields, idempotency key, key header) and how errors map. */
class StripeApiGatewayTest {

    private MockRestServiceServer server;
    private StripeApiGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://stripe.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer sk_test_not_a_real_key");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new StripeApiGateway(builder.build());
    }

    @Test
    void createCustomer_sendsTheFormWithAnIdempotencyKey_andReturnsTheId() {
        UUID userId = UUID.randomUUID();
        server.expect(requestTo("https://stripe.test/v1/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "app-user-" + userId))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk_test_not_a_real_key"))
                .andExpect(content().string(containsString("metadata%5Bapp_user_id%5D=" + userId)))
                .andExpect(content().string(containsString("email=jamie%40test.local")))
                .andRespond(withSuccess("{\"id\":\"cus_123\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.createCustomer("jamie@test.local", "Jamie Cruz", userId, "app-user-" + userId)).isEqualTo("cus_123");
        server.verify();
    }

    @Test
    void createSetupIntent_asksForAnOffSessionCardSetup_andReturnsTheClientSecret() {
        server.expect(requestTo("https://stripe.test/v1/setup_intents"))
                .andExpect(content().string(containsString("customer=cus_123")))
                .andExpect(content().string(containsString("usage=off_session")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("payment_method_types"))))   // Stripe no longer accepts it
                .andRespond(withSuccess("{\"id\":\"seti_1\",\"client_secret\":\"seti_1_secret_x\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.createSetupIntent("cus_123")).isEqualTo("seti_1_secret_x");
        server.verify();
    }

    @Test
    void attachAndDescribe_attachesAFreePaymentMethod_andReadsTheCard() {
        server.expect(requestTo("https://stripe.test/v1/payment_methods/pm_abc"))
                .andRespond(withSuccess("{\"id\":\"pm_abc\",\"customer\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://stripe.test/v1/payment_methods/pm_abc/attach"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("customer=cus_123")))
                .andRespond(withSuccess("{\"id\":\"pm_abc\",\"card\":{\"brand\":\"visa\",\"last4\":\"4242\",\"exp_month\":12,\"exp_year\":2030}}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.attachAndDescribe("cus_123", "pm_abc")).isEqualTo(new StripeGateway.CardDetails("visa", "4242", 12, 2030));
        server.verify();
    }

    @Test
    void attachAndDescribe_aPaymentMethodAlreadyOnThisCustomer_isNotAttachedAgain() {
        server.expect(requestTo("https://stripe.test/v1/payment_methods/pm_abc"))
                .andRespond(withSuccess("{\"id\":\"pm_abc\",\"customer\":\"cus_123\",\"card\":{\"brand\":\"mastercard\",\"last4\":\"4444\",\"exp_month\":1,\"exp_year\":2031}}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.attachAndDescribe("cus_123", "pm_abc").last4()).isEqualTo("4444");
        server.verify();
    }

    @Test
    void attachAndDescribe_aPaymentMethodOfAnotherCustomer_isRefused() {
        server.expect(requestTo("https://stripe.test/v1/payment_methods/pm_abc"))
                .andRespond(withSuccess("{\"id\":\"pm_abc\",\"customer\":\"cus_someone_else\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.attachAndDescribe("cus_123", "pm_abc")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void detach_callsTheDetachEndpoint() {
        server.expect(requestTo("https://stripe.test/v1/payment_methods/pm_abc/detach"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"pm_abc\",\"customer\":null}", MediaType.APPLICATION_JSON));

        gateway.detach("pm_abc");
        server.verify();
    }

    @Test
    void aStripe4xx_isABadRequest_andA5xx_isABadGateway() {
        server.expect(requestTo("https://stripe.test/v1/payment_methods/pm_bad/detach"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":\"resource_missing\"}}"));
        server.expect(requestTo("https://stripe.test/v1/payment_methods/pm_down/detach"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> gateway.detach("pm_bad")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> gateway.detach("pm_down")).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void theApplicationRefusesToStartInStripeModeWithoutAKey() {
        StripeClientConfig config = new StripeClientConfig();
        assertThatThrownBy(() -> config.stripeRestClient("", "https://stripe.test")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.stripeRestClient("  ", "https://stripe.test")).isInstanceOf(IllegalStateException.class);
        assertThat(config.stripeRestClient("sk_test_not_a_real_key", "https://stripe.test")).isNotNull();
    }

    @Test
    void theMockGatewayNeverNeedsStripe() {
        MockStripeGateway mock = new MockStripeGateway();
        UUID userId = UUID.randomUUID();

        assertThat(mock.live()).isFalse();
        assertThat(mock.createSetupIntent("x")).isEqualTo("seti_mock_secret");
        assertThat(mock.createCustomer("a@b.c", "A B", userId, "k")).isEqualTo("cus_mock_" + userId);
        mock.detach("pm_anything");
    }
}
