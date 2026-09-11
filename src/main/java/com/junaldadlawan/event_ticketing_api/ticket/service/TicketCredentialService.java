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
 * Format: {@code ticketId + ":" + version + "." + base64url(HMAC-SHA256(ticketId + ":" + version, secret))}.
 * <p>
 * The credential carries no expiry/validity claim of its own — a ticket's
 * real validity lives in {@code Ticket.status}, checked at scan-time (Phase
 * 10's job, not this one). Its only two jobs are being unguessable (it
 * embeds a random UUID) and tamper-evident (the signature proves this server
 * issued it). Uses its own dedicated secret ({@code
 * app.ticket.credential.secret}), never the auth-token JWT secret.
 * <p>
 * The embedded {@code version} is Phase 7's addition (BR-TRANSFER-005): a
 * ticket row persists across transfers/resales (same ticket id - see the
 * ERD's "Ticket ||--o{ TicketTransfer"), so re-deriving a credential from the
 * bare ticket id alone would always produce the SAME string, unable to
 * invalidate anything. Bumping {@code Ticket.credentialVersion} and folding
 * it into the signed payload is what makes "issue a new credential for the
 * same ticket" actually change the credential. Every ticket starts at
 * version 0 at checkout issuance (the 1-arg overload).
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

    /** Convenience for fresh issuance at checkout — always version 0. */
    public String generate(UUID ticketId) {
        return generate(ticketId, 0);
    }

    public String generate(UUID ticketId, int version) {
        String payload = ticketId + ":" + version;
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
