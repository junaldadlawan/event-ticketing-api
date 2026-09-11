package com.junaldadlawan.event_ticketing_api.ticket.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
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
 * Generation AND verification (Phase 10, {@code /check-in/validate}) both
 * live here - {@link #verify} is the counterpart {@link #generate}
 * javadoc'd itself as deferred. Verification never trusts the CLAIMED
 * ticket id/version embedded in a scanned credential without first
 * recomputing and comparing the signature: a forged credential (right
 * shape, wrong signature) is indistinguishable from garbage and returns
 * {@link Optional#empty()}, never a parsed-but-unsigned result.
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
        return payload + "." + sign(payload);
    }

    /**
     * Parses and verifies a raw scanned credential. Returns {@link
     * Optional#empty()} for anything that doesn't parse as {@code
     * ticketId:version.signature} (malformed, corrupted, or simply not a
     * credential this server ever issued) OR whose signature doesn't match
     * a freshly-recomputed one for the claimed payload - the two failure
     * modes are deliberately indistinguishable to the caller, so a forged
     * credential can't be told apart from noise. Does NOT check the
     * embedded {@code version} against {@code Ticket.credentialVersion} -
     * that requires a DB lookup by {@code ticketId}, which is the caller's
     * job (see {@code CheckInServiceImpl.validate}); a version mismatch
     * means "superseded by a later transfer/resale" (BR-TRANSFER-005), a
     * different failure mode than "signature invalid".
     */
    public Optional<ParsedCredential> verify(String rawCredential) {
        if (rawCredential == null) {
            return Optional.empty();
        }
        String[] parts = rawCredential.split("\\.", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        String payload = parts[0];
        String providedSignature = parts[1];

        String[] payloadParts = payload.split(":", 2);
        if (payloadParts.length != 2) {
            return Optional.empty();
        }
        UUID ticketId;
        int version;
        try {
            ticketId = UUID.fromString(payloadParts[0]);
            version = Integer.parseInt(payloadParts[1]);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        String expectedSignature = sign(payload);
        boolean signatureMatches = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8));
        if (!signatureMatches) {
            return Optional.empty();
        }
        return Optional.of(new ParsedCredential(ticketId, version));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to sign ticket credential", e);
        }
    }

    /** A credential whose signature has been verified - {@code ticketId}/{@code version} are trustworthy. */
    public record ParsedCredential(UUID ticketId, int version) {
    }
}
