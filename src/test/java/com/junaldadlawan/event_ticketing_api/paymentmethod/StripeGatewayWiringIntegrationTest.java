package com.junaldadlawan.event_ticketing_api.paymentmethod;

import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.order.gateway.StripePaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.paymentmethod.gateway.StripeApiGateway;
import com.junaldadlawan.event_ticketing_api.paymentmethod.gateway.StripeGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application starts in {@code payments.gateway=stripe} mode and wires the real Stripe gateway (a dummy key, no network
 * call is made at startup). Without this the stripe mode could only fail when a person starts the app.
 */
@SpringBootTest(properties = {"payments.gateway=stripe", "payments.stripe.secret-key=sk_test_dummy_not_a_real_key"})
class StripeGatewayWiringIntegrationTest {

    @Autowired
    private StripeGateway stripeGateway;
    @Autowired
    private PaymentGatewayClient paymentGatewayClient;

    @Test
    void stripeMode_startsTheApplication_withTheRealGateway() {
        assertThat(stripeGateway).isInstanceOf(StripeApiGateway.class);
        assertThat(stripeGateway.live()).isTrue();
    }

    @Test
    void stripeMode_chargesCheckoutsThroughStripe_notThroughTheMock() {
        assertThat(paymentGatewayClient).isInstanceOf(StripePaymentGatewayClient.class);
    }
}
