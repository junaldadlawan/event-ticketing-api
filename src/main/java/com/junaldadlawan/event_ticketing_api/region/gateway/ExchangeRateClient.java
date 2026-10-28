package com.junaldadlawan.event_ticketing_api.region.gateway;

import com.junaldadlawan.event_ticketing_api.region.entity.SupportedCurrency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * The exchange-rate feed: Frankfurter (ECB reference rates, no API key). {@code GET {base}/latest?base=USD} answers
 * {@code {"base":"USD","date":"...","rates":{"PHP":58.12,...}}}. The base URL comes from {@code EXCHANGE_RATES_BASE_URL}.
 */
@Component
public class ExchangeRateClient {

    public static final String SOURCE = "frankfurter";

    private final RestClient http;

    public ExchangeRateClient(@Value("${app.exchange-rates.base-url:https://api.frankfurter.dev/v1}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Units of each currency per 1 USD; throws (any RuntimeException) when the feed can't be read. */
    public Map<String, BigDecimal> usdRates() {
        JsonNode body = http.get().uri("/latest?base={base}", SupportedCurrency.REFERENCE).retrieve().body(JsonNode.class);
        if (body == null || !body.path("rates").isObject()) {
            throw new IllegalStateException("The exchange-rate feed answered without rates");
        }
        Map<String, BigDecimal> rates = new HashMap<>();
        body.path("rates").properties().forEach(entry -> rates.put(entry.getKey(), entry.getValue().decimalValue()));
        return rates;
    }
}
