package com.junaldadlawan.event_ticketing_api.order.controller;

import com.junaldadlawan.event_ticketing_api.order.gateway.PayMongoWebhookVerifier;
import com.junaldadlawan.event_ticketing_api.order.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * PayMongo's callback: tells us a payment succeeded or failed after the buyer entered a card in the browser. Public in
 * SecurityConfig (PayMongo sends no login), so the {@code Paymongo-Signature} header is the only authentication and is checked
 * against the raw body before anything is read. Only exists when {@code payments.gateway=paymongo}. Always answers 200 for a valid
 * event, even one it ignores, so PayMongo does not keep re-sending it; processing is idempotent.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/paymongo")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payments.gateway", havingValue = "paymongo")
public class PayMongoWebhookController {

    private final PayMongoWebhookVerifier verifier;
    private final CheckoutService checkoutService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody byte[] body,
                                        @RequestHeader(value = "Paymongo-Signature", required = false) String signature) {
        if (!verifier.verify(body, signature)) {
            log.warn("PayMongo webhook rejected: bad signature");
            return ResponseEntity.status(401).build();
        }
        JsonNode event;
        try {
            event = objectMapper.readTree(body);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
        JsonNode attributes = event.path("data").path("attributes");
        String type = attributes.path("type").asText("");
        JsonNode payment = attributes.path("data");
        JsonNode paymentAttributes = payment.path("attributes");
        String intentId = paymentAttributes.path("payment_intent_id").asText("");
        if (intentId.isBlank()) {
            return ResponseEntity.ok().build();
        }
        switch (type) {
            case "payment.paid" -> checkoutService.onPaymentPaid(intentId, payment.path("id").asText(),
                    paymentAttributes.path("amount").asLong(), paymentAttributes.path("currency").asText("PHP"));
            case "payment.failed" -> checkoutService.onPaymentFailed(intentId,
                    paymentAttributes.path("failed_message").asText(""));
            default -> log.debug("PayMongo webhook ignored: type={}", type);
        }
        return ResponseEntity.ok().build();
    }
}
