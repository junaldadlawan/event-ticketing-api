package com.junaldadlawan.event_ticketing_api.paymentmethod.gateway;

import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Saved cards on real Stripe, over its REST API with the shared client from {@link StripeClientConfig} (the secret key comes
 * from the environment). Used only when {@code payments.gateway=stripe}. Responses and errors are never logged beyond the HTTP
 * status: they can contain card details.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "stripe")
public class StripeApiGateway implements StripeGateway {

    private final RestClient http;

    public StripeApiGateway(@Qualifier("stripeRestClient") RestClient http) {
        this.http = http;
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public String createCustomer(String email, String name, UUID userId, String idempotencyKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", email);
        form.add("name", name);
        form.add("metadata[app_user_id]", userId.toString());
        JsonNode customer = call(() -> http.post().uri("/v1/customers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class));
        return customer.get("id").asText();
    }

    @Override
    public String createSetupIntent(String customerId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", customerId);
        form.add("usage", "off_session");
        // no payment_method_types: Stripe no longer accepts it, the payment methods come from the Dashboard settings
        JsonNode intent = call(() -> http.post().uri("/v1/setup_intents")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class));
        return intent.get("client_secret").asText();
    }

    @Override
    public CardDetails attachAndDescribe(String customerId, String paymentMethodId) {
        JsonNode method = call(() -> http.get().uri("/v1/payment_methods/{id}", paymentMethodId).retrieve().body(JsonNode.class));
        JsonNode owner = method.get("customer");
        if (owner != null && !owner.isNull() && !customerId.equals(owner.asText())) {
            throw new BadRequestException("That card cannot be saved to this account");
        }
        if (owner == null || owner.isNull()) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("customer", customerId);
            method = call(() -> http.post().uri("/v1/payment_methods/{id}/attach", paymentMethodId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class));
        }
        JsonNode card = method.get("card");
        if (card == null || card.isNull()) {
            throw new BadRequestException("Only cards can be saved");
        }
        return new CardDetails(card.get("brand").asText(), card.get("last4").asText(),
                card.get("exp_month").asInt(), card.get("exp_year").asInt());
    }

    @Override
    public void detach(String paymentMethodId) {
        call(() -> http.post().uri("/v1/payment_methods/{id}/detach", paymentMethodId).retrieve().body(JsonNode.class));
    }

    /** A Stripe 4xx is the caller's problem (400); anything else is the provider being unavailable (502). */
    private static JsonNode call(Supplier<JsonNode> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            log.warn("Stripe call failed: status={}", e.getStatusCode().value());
            if (e.getStatusCode().is4xxClientError()) {
                throw new BadRequestException("The payment provider rejected the request");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The payment provider is unavailable");
        } catch (ResourceAccessException e) {
            log.warn("Stripe call failed: could not reach the provider");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The payment provider is unavailable");
        }
    }
}
