package com.junaldadlawan.event_ticketing_api.order.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The one HTTP client for PayMongo. Only exists when {@code payments.gateway=paymongo}; the application refuses to start in that mode
 * without {@code PAYMONGO_SECRET_KEY}. PayMongo uses HTTP Basic auth with the secret key as the user name and an empty password.
 */
@Configuration
@ConditionalOnProperty(name = "payments.gateway", havingValue = "paymongo")
public class PayMongoClientConfig {

    @Bean("paymongoRestClient")
    public RestClient paymongoRestClient(@Value("${payments.paymongo.secret-key:}") String secretKey,
                                         @Value("${payments.paymongo.base-url:https://api.paymongo.com/v1}") String baseUrl) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("payments.gateway=paymongo needs the PAYMONGO_SECRET_KEY environment variable");
        }
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return RestClient.builder().baseUrl(baseUrl).defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic).build();
    }
}
