package com.junaldadlawan.event_ticketing_api.order.gateway;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Payments on PayMongo ({@code payments.gateway=paymongo}). The card is entered in the browser with PayMongo's public key, so the
 * server never sees it: checkout only creates a payment intent here ({@link #createPaymentIntent}) and the order is issued when the
 * {@code payment.paid} webhook arrives. The payment id on the order ({@code pay_...}) is what a refund needs. Amounts are centavos.
 * Nothing about a card is logged.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "paymongo")
public class PayMongoPaymentGatewayClient implements PaymentGatewayClient {

    private static final String CURRENCY = "PHP";

    private final RestClient http;

    public PayMongoPaymentGatewayClient(@Qualifier("paymongoRestClient") RestClient http) {
        this.http = http;
    }

    @Override
    public boolean confirmsByWebhook() {
        return true;
    }

    @Override
    public PaymentIntentInfo createPaymentIntent(Money amount, String description, String reference) {
        if (!CURRENCY.equalsIgnoreCase(amount.getCurrency())) {
            throw new BadRequestException("PayMongo only accepts " + CURRENCY + " orders, not " + amount.getCurrency());
        }
        Map<String, Object> attributes = Map.of(
                "amount", amount.getAmount(),
                "currency", CURRENCY,
                "payment_method_allowed", List.of("card"),
                "payment_method_options", Map.of("card", Map.of("request_three_d_secure", "automatic")),
                "capture_type", "automatic",
                "description", description,
                "metadata", Map.of("reference", reference));
        ResponseEntity<JsonNode> response = post("/payment_intents", Map.of("data", Map.of("attributes", attributes)));
        if (response.getStatusCode().isError() || response.getBody() == null) {
            if (response.getStatusCode().is5xxServerError() || response.getBody() == null) {
                throw unavailable();
            }
            throw new BadRequestException("The payment provider rejected the request: " + errorDetail(response.getBody(), "invalid payment"));
        }
        JsonNode data = response.getBody().path("data");
        return new PaymentIntentInfo(data.path("id").asText(), data.path("attributes").path("client_key").asText());
    }

    /** Not used with PayMongo: the card is attached in the browser, never sent to this server as a token. */
    @Override
    public PaymentResult charge(String paymentMethodToken, Money amount) {
        return PaymentResult.failure("This payment cannot be made with a token: the card is entered in the browser during checkout");
    }

    @Override
    public PaymentResult refund(String gatewayRef, Money amount) {
        if (gatewayRef == null || !gatewayRef.startsWith("pay_")) {
            return PaymentResult.failure("This payment was not made through PayMongo, so it cannot be refunded there");
        }
        Map<String, Object> attributes = Map.of(
                "payment_id", gatewayRef,
                "amount", amount.getAmount(),
                "reason", "others",
                "notes", "Event ticket refund");
        ResponseEntity<JsonNode> response = post("/refunds", Map.of("data", Map.of("attributes", attributes)));
        if (response.getStatusCode().is5xxServerError() || response.getBody() == null) {
            throw unavailable();
        }
        if (response.getStatusCode().isError()) {
            return PaymentResult.failure(errorDetail(response.getBody(), "The refund was declined"));
        }
        JsonNode data = response.getBody().path("data");
        if ("failed".equals(data.path("attributes").path("status").asText())) {
            return PaymentResult.failure("The refund failed");
        }
        return PaymentResult.success(data.path("id").asText());
    }

    /** A PayMongo error status does not throw here: its JSON body ({@code errors[].detail}) says what happened. */
    private ResponseEntity<JsonNode> post(String path, Map<String, Object> body) {
        try {
            return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> { }).toEntity(JsonNode.class);
        } catch (ResourceAccessException e) {
            log.warn("PayMongo call failed: could not reach the provider");
            throw unavailable();
        }
    }

    private static String errorDetail(JsonNode body, String fallback) {
        String detail = body.path("errors").path(0).path("detail").asText("");
        return detail.isBlank() ? fallback : detail;
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "The payment provider is unavailable");
    }
}
