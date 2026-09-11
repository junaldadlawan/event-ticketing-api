package com.junaldadlawan.event_ticketing_api.ticket.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * Generates a ticket's scannable credential (Phase 6a confirmed decision #1):
 * a dedicated HMAC-SHA256-signed opaque token, deliberately NOT a JWT.
 * Format: {@code ticketId + "." + base64url(HMAC-SHA256(ticketId.toString(), secret))}.
 * <p>
 * The credential carries no expiry/validity claim of its own — a ticket's
 * real validity lives in {@code Ticket.status}, checked at scan-time (Phase
 * 10's job, not this one). Its only two jobs are being unguessable (it
 * embeds a random UUID) and tamper-evident (the signature proves this server
 * issued it). Uses its own dedicated secret ({@code
 * app.ticket.credential.secret}), never the auth-token JWT secret.
 * <p>
 * Generation only — no verification/lookup method here. That's Phase 10's
 * check-in validation job.
 */
@Component
public class TicketCredentialService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec keySpec;

    public TicketCredentialService(@Value("${app.ticket.credential.secret}") String secret) {
        this.keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String generate(UUID ticketId) {
        String payload = ticketId.toString();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            return payload + "." + encodedSignature;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to generate ticket credential", e);
        }
    }
}
