package com.junaldadlawan.event_ticketing_api.order.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Checks that a webhook really comes from PayMongo. The {@code Paymongo-Signature} header looks like
 * {@code t=<timestamp>,te=<test signature>,li=<live signature>}; a signature is the hex HMAC-SHA256 of
 * {@code "<timestamp>.<raw body>"} with the webhook secret. Either {@code te} or {@code li} matching is accepted (PayMongo fills the
 * one for the mode the event came from). Compared in constant time. There is no timestamp window on purpose: PayMongo re-delivers
 * failed webhooks later, and processing is idempotent by checkout state anyway.
 */
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "paymongo")
public class PayMongoWebhookVerifier {

    private final byte[] secret;

    public PayMongoWebhookVerifier(@Value("${payments.paymongo.webhook-secret:}") String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("payments.gateway=paymongo needs the PAYMONGO_WEBHOOK_SECRET environment variable");
        }
        this.secret = webhookSecret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean verify(byte[] rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        Map<String, String> parts = new HashMap<>();
        for (String part : signatureHeader.split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                parts.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
        }
        String timestamp = parts.get("t");
        if (timestamp == null) {
            return false;
        }
        byte[] expected = sign(timestamp + "." + new String(rawBody, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        for (String key : new String[] {"te", "li"}) {
            String given = parts.get(key);
            if (given != null && !given.isEmpty() && MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    /** Hex HMAC-SHA256 of the payload with the webhook secret (public so tests can build a valid header). */
    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not compute the webhook signature", e);
        }
    }
}
