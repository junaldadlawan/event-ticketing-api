package com.junaldadlawan.event_ticketing_api.paymentmethod.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * The one HTTP client for Stripe, shared by saved cards ({@link StripeApiGateway}) and checkout charges and refunds
 * ({@code StripePaymentGatewayClient}). Only exists when {@code payments.gateway=stripe}; the application refuses to start
 * in that mode without {@code STRIPE_SECRET_KEY}.
 */
@Configuration
@ConditionalOnProperty(name = "payments.gateway", havingValue = "stripe")
public class StripeClientConfig {

    @Bean("stripeRestClient")
    public RestClient stripeRestClient(@Value("${payments.stripe.secret-key:}") String secretKey,
                                       @Value("${payments.stripe.base-url:https://api.stripe.com}") String baseUrl) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("payments.gateway=stripe needs the STRIPE_SECRET_KEY environment variable");
        }
        return RestClient.builder().baseUrl(baseUrl).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey).build();
    }
}
