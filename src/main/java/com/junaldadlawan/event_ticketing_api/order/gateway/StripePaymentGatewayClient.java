package com.junaldadlawan.event_ticketing_api.order.gateway;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Real charges and refunds through Stripe, used when {@code payments.gateway=stripe} (the mock is the default).
 * A charge is a confirmed PaymentIntent for the buyer's card in the order's currency; only a {@code succeeded} one counts as
 * paid. A decline, or a card that needs 3-D Secure (not supported yet), is a failed payment ({@link PaymentResult#failure}),
 * never an exception; only an unreachable or broken Stripe throws (502). The {@code gatewayRef} stored on the payment is the
 * PaymentIntent id ({@code pi_...}), which is what a refund later needs. Nothing about a card is logged.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "stripe")
public class StripePaymentGatewayClient implements PaymentGatewayClient {

    private final RestClient http;

    public StripePaymentGatewayClient(@Qualifier("stripeRestClient") RestClient http) {
        this.http = http;
    }

    @Override
    public PaymentResult charge(String paymentMethodToken, Money amount) {
        return charge(paymentMethodToken, null, amount, null);
    }

    @Override
    public PaymentResult charge(String paymentMethodToken, String customerId, Money amount) {
        return charge(paymentMethodToken, customerId, amount, null);
    }

    @Override
    public PaymentResult charge(String paymentMethodToken, String customerId, Money amount, String idempotencyKey) {
        if (paymentMethodToken == null || !paymentMethodToken.startsWith("pm_")) {
            return PaymentResult.failure("The payment method is not a Stripe payment method (pm_...)");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(amount.getAmount()));
        form.add("currency", amount.getCurrency().toLowerCase());
        form.add("payment_method", paymentMethodToken);
        form.add("confirm", "true");
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("automatic_payment_methods[allow_redirects]", "never");   // no redirect step is possible in this flow
        form.add("description", "Event ticket order");
        if (customerId != null) {
            form.add("customer", customerId);
        }
        ResponseEntity<JsonNode> response = post("/v1/payment_intents", form, idempotencyKey);
        JsonNode body = response.getBody();
        if (response.getStatusCode().is5xxServerError() || body == null) {
            throw unavailable();
        }
        if (response.getStatusCode().isError()) {
            return PaymentResult.failure(errorMessage(body, "The payment was declined"));
        }
        String status = body.path("status").asText();
        String id = body.path("id").asText();
        if ("succeeded".equals(status)) {
            return PaymentResult.success(id);
        }
        // requires_action (3-D Secure) and anything else unfinished: nothing is charged, so cancel the intent and fail.
        cancel(id);
        return PaymentResult.failure("requires_action".equals(status)
                ? "The card needs extra authentication (3-D Secure), which is not supported yet"
                : "The payment could not be completed");
    }

    @Override
    public PaymentResult refund(String gatewayRef, Money amount) {
        if (gatewayRef == null || !gatewayRef.startsWith("pi_")) {
            return PaymentResult.failure("This payment was not made through Stripe, so it cannot be refunded there");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", gatewayRef);
        form.add("amount", String.valueOf(amount.getAmount()));
        ResponseEntity<JsonNode> response = post("/v1/refunds", form, null);
        JsonNode body = response.getBody();
        if (response.getStatusCode().is5xxServerError() || body == null) {
            throw unavailable();
        }
        if (response.getStatusCode().isError()) {
            return PaymentResult.failure(errorMessage(body, "The refund was declined"));
        }
        String status = body.path("status").asText();
        if ("failed".equals(status) || "canceled".equals(status)) {
            return PaymentResult.failure("The refund failed");
        }
        return PaymentResult.success(body.path("id").asText());
    }

    private void cancel(String paymentIntentId) {
        try {
            post("/v1/payment_intents/" + paymentIntentId + "/cancel", new LinkedMultiValueMap<>(), null);
        } catch (RuntimeException e) {
            log.warn("Could not cancel an unfinished Stripe payment");
        }
    }

    /** A Stripe error status does not throw here: its JSON body says what happened (card_declined and so on). */
    private ResponseEntity<JsonNode> post(String path, MultiValueMap<String, String> form, String idempotencyKey) {
        try {
            RestClient.RequestBodySpec request = http.post().uri(path).contentType(MediaType.APPLICATION_FORM_URLENCODED);
            if (idempotencyKey != null) {
                request = request.header("Idempotency-Key", idempotencyKey);
            }
            return request.body(form).retrieve().onStatus(status -> status.isError(), (req, res) -> { }).toEntity(JsonNode.class);
        } catch (ResourceAccessException e) {
            log.warn("Stripe call failed: could not reach the provider");
            throw unavailable();
        }
    }

    private static String errorMessage(JsonNode body, String fallback) {
        String message = body.path("error").path("message").asText("");
        return message.isBlank() ? fallback : message;
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "The payment provider is unavailable");
    }
}
