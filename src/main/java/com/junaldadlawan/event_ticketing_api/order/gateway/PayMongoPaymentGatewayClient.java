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
        return createPaymentIntent(amount, description, reference, null);
    }

    @Override
    public PaymentIntentInfo createPaymentIntent(Money amount, String description, String reference, String saveForCustomerId) {
        if (!CURRENCY.equalsIgnoreCase(amount.getCurrency())) {
            throw new BadRequestException("PayMongo only accepts " + CURRENCY + " orders, not " + amount.getCurrency());
        }
        Map<String, Object> attributes = new java.util.HashMap<>(Map.of(
                "amount", amount.getAmount(),
                "currency", CURRENCY,
                "payment_method_allowed", List.of("card"),
                "payment_method_options", Map.of("card", Map.of("request_three_d_secure", "automatic")),
                "capture_type", "automatic",
                "description", description,
                "metadata", Map.of("reference", reference)));
        if (saveForCustomerId != null) {
            // the card is vaulted on this customer once the payment succeeds (on-session: the security code is asked again at each use)
            attributes.put("setup_future_usage", Map.of("session_type", "on_session", "customer_id", saveForCustomerId));
        }
        ResponseEntity<JsonNode> response = post("/payment_intents", Map.of("data", Map.of("attributes", attributes)));
        if (response.getStatusCode().isError() || response.getBody() == null) {
            if (response.getStatusCode().is5xxServerError() || response.getBody() == null) {
                throw unavailable();
            }
            String detail = errorDetail(response.getBody(), "invalid payment");
            if (saveForCustomerId != null && (detail.toLowerCase().contains("setup_future_usage") || detail.toLowerCase().contains("on session payments"))) {
                // vaulting is not enabled on this account: not a problem with the payment itself
                throw new CardSavingUnavailableException("The payment provider rejected the request: " + detail);
            }
            throw new BadRequestException("The payment provider rejected the request: " + detail);
        }
        JsonNode data = response.getBody().path("data");
        return new PaymentIntentInfo(data.path("id").asText(), data.path("attributes").path("client_key").asText());
    }

    @Override
    public String createCustomer(String email, String firstName, String lastName) {
        Map<String, Object> attributes = Map.of("first_name", firstName, "last_name", lastName, "email", email,
                "default_device", "email");   // required by PayMongo; the phone number is optional and not sent
        ResponseEntity<JsonNode> response = post("/customers", Map.of("data", Map.of("attributes", attributes)));
        if (response.getStatusCode().is5xxServerError() || response.getBody() == null) {
            throw unavailable();
        }
        if (response.getStatusCode().isError()) {
            throw new BadRequestException("The payment provider rejected the customer: " + errorDetail(response.getBody(), "invalid customer"));
        }
        return response.getBody().path("data").path("id").asText();
    }

    @Override
    public List<VaultedCard> customerCards(String customerId) {
        ResponseEntity<JsonNode> response = get("/customers/" + customerId + "/payment_methods");
        if (response.getStatusCode().is5xxServerError() || response.getBody() == null) {
            throw unavailable();
        }
        if (response.getStatusCode().isError()) {
            throw new BadRequestException("The saved cards could not be read: " + errorDetail(response.getBody(), "invalid customer"));
        }
        java.util.List<VaultedCard> cards = new java.util.ArrayList<>();
        for (JsonNode method : response.getBody().path("data")) {
            JsonNode details = method.path("attributes").path("details");
            String brand = details.path("card_brand").asText(details.path("brand").asText("card"));
            if (method.path("id").asText("").startsWith("pm_") && details.hasNonNull("last4")) {
                cards.add(new VaultedCard(method.path("id").asText(), brand, details.path("last4").asText(),
                        details.path("exp_month").asInt(), details.path("exp_year").asInt()));
            }
        }
        return cards;
    }

    @Override
    public AttachOutcome attachSavedCard(String intentId, String clientKey, String paymentMethodId, String securityCode, String returnUrl) {
        if (securityCode != null && !securityCode.isBlank()) {
            // on-session vaulting: PayMongo wants the security code entered again, set on the payment method just before it is attached
            ResponseEntity<JsonNode> updated = patch("/payment_methods/" + paymentMethodId,
                    Map.of("data", Map.of("attributes", Map.of("details", Map.of("cvc", securityCode)))));
            if (updated.getStatusCode().is5xxServerError() || updated.getBody() == null) {
                throw unavailable();
            }
            if (updated.getStatusCode().isError()) {
                return new AttachOutcome("failed", null, errorDetail(updated.getBody(), "The saved card could not be used"));
            }
        }
        Map<String, Object> attributes = Map.of("payment_method", paymentMethodId, "client_key", clientKey, "return_url", returnUrl);
        ResponseEntity<JsonNode> response = post("/payment_intents/" + intentId + "/attach", Map.of("data", Map.of("attributes", attributes)));
        if (response.getStatusCode().is5xxServerError() || response.getBody() == null) {
            throw unavailable();
        }
        if (response.getStatusCode().isError()) {
            return new AttachOutcome("failed", null, errorDetail(response.getBody(), "The payment was declined"));
        }
        JsonNode intent = response.getBody().path("data").path("attributes");
        String status = intent.path("status").asText();
        if ("awaiting_next_action".equals(status)) {
            String url = intent.path("next_action").path("redirect").path("url").asText("");
            return url.isBlank() ? new AttachOutcome(status, null, "The bank's confirmation page is missing") : new AttachOutcome(status, url, null);
        }
        if ("awaiting_payment_method".equals(status)) {
            String reason = intent.path("last_payment_error").path("failed_message").asText("");
            return new AttachOutcome(status, null, reason.isBlank() ? "The payment was declined" : reason);
        }
        return new AttachOutcome(status, null, null);   // succeeded or processing: the webhook completes the order
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

    private ResponseEntity<JsonNode> get(String path) {
        try {
            return http.get().uri(path).retrieve().onStatus(status -> status.isError(), (req, res) -> { }).toEntity(JsonNode.class);
        } catch (ResourceAccessException e) {
            log.warn("PayMongo call failed: could not reach the provider");
            throw unavailable();
        }
    }

    private ResponseEntity<JsonNode> patch(String path, Map<String, Object> body) {
        try {
            return http.patch().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
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
