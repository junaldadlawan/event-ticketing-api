package com.junaldadlawan.event_ticketing_api.gatewayfee.gateway;

import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads PayMongo's own payment records ({@code GET /v1/payments}): each carries the {@code fee} PayMongo took, the {@code foreign_fee}
 * surcharge for a card issued abroad, and the {@code net_amount} left. PayMongo has no endpoint for its fee rates, so the rate is worked
 * out from these. Read-only; only paid payments are returned. Card details are limited to brand and issuing country.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "paymongo")
public class PayMongoPaymentFeeSampler implements PaymentFeeSampler {

    private final RestClient http;

    public PayMongoPaymentFeeSampler(@Qualifier("paymongoRestClient") RestClient http) {
        this.http = http;
    }

    @Override
    public String provider() {
        return "PAYMONGO";
    }

    @Override
    public List<PaymentSample> recentPaidPayments(int limit) {
        ResponseEntity<JsonNode> response;
        try {
            response = http.get().uri("/payments?limit=" + limit).retrieve().onStatus(status -> status.isError(), (req, res) -> { })
                    .toEntity(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("PayMongo payment read failed: {}", e.getClass().getSimpleName());
            throw unavailable();
        }
        if (response.getStatusCode().isError() || response.getBody() == null) {
            throw unavailable();
        }
        List<PaymentSample> samples = new ArrayList<>();
        for (JsonNode payment : response.getBody().path("data")) {
            JsonNode a = payment.path("attributes");
            if (!"paid".equals(a.path("status").asText())) {
                continue;
            }
            JsonNode source = a.path("source");
            samples.add(new PaymentSample(payment.path("id").asText(), a.path("amount").asLong(), a.path("currency").asText("PHP"),
                    a.path("fee").asLong(), a.path("foreign_fee").asLong(), a.path("tax_amount").asLong(), a.path("net_amount").asLong(),
                    source.path("type").asText(null), source.path("brand").asText(null), source.path("country").asText(null),
                    a.hasNonNull("paid_at") ? a.path("paid_at").asLong() : null, a.path("livemode").asBoolean(false)));
        }
        return samples;
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "PayMongo is unavailable, so its fees could not be read");
    }
}
