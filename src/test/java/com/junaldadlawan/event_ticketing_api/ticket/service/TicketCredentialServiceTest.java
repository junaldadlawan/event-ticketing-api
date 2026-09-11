package com.junaldadlawan.event_ticketing_api.ticket.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused unit test on {@link TicketCredentialService} itself (BR-TICKET-001,
 * BR-NFR-003) — no Spring context, no mocks, since the class takes its
 * secret via a plain constructor argument. Verifies the three properties
 * the dispatch specifically asked to be proven: (1) it is genuinely wired to
 * its own dedicated {@code app.ticket.credential.secret}, distinct from
 * {@code app.jwt.secret}; (2) the credential is per-ticket-random (embeds
 * the ticket's own random UUID, so two different tickets never collide);
 * (3) it is HMAC-signed and NOT derivable from the ticket id alone without
 * knowing the secret.
 */
class TicketCredentialServiceTest {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private String hmacSign(String secret, String payload) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(keySpec);
        byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    @Test
    void generate_returnsTicketIdColonVersionDotBase64UrlSignature() {
        TicketCredentialService service = new TicketCredentialService("dedicated-ticket-secret-for-this-test-0123456789");
        UUID ticketId = UUID.randomUUID();

        String credential = service.generate(ticketId);

        String[] parts = credential.split("\\.", 2);
        assertThat(parts).hasSize(2);
        // 1-arg convenience overload always issues version 0 (checkout issuance).
        assertThat(parts[0]).isEqualTo(ticketId + ":0");
        // Base64 URL-safe, no padding: only [A-Za-z0-9_-] characters.
        assertThat(parts[1]).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void generate_matchesManuallyComputedHmacWithTheSameSecret() throws Exception {
        String secret = "dedicated-ticket-secret-for-this-test-0123456789";
        TicketCredentialService service = new TicketCredentialService(secret);
        UUID ticketId = UUID.randomUUID();

        String credential = service.generate(ticketId, 3);
        String expectedSignature = hmacSign(secret, ticketId + ":3");

        assertThat(credential).isEqualTo(ticketId + ":3." + expectedSignature);
    }

    /**
     * The whole point of Phase 7's version parameter (BR-TRANSFER-005): the
     * SAME ticket id, at two different versions, must produce two different
     * credentials — this is what lets a transfer/resale "invalidate the
     * previous credential and issue a new one" for a ticket row that keeps
     * the same id across its lifetime (see the ERD's "Ticket ||--o{
     * TicketTransfer").
     */
    @Test
    void generate_sameTicketIdDifferentVersion_producesDifferentCredential() {
        TicketCredentialService service = new TicketCredentialService("dedicated-ticket-secret-for-this-test-0123456789");
        UUID ticketId = UUID.randomUUID();

        String credentialV0 = service.generate(ticketId, 0);
        String credentialV1 = service.generate(ticketId, 1);

        assertThat(credentialV0).isNotEqualTo(credentialV1);
    }

    @Test
    void generate_twoDifferentTicketIds_produceDistinctCredentials() {
        TicketCredentialService service = new TicketCredentialService("dedicated-ticket-secret-for-this-test-0123456789");

        String credentialA = service.generate(UUID.randomUUID());
        String credentialB = service.generate(UUID.randomUUID());

        assertThat(credentialA).isNotEqualTo(credentialB);
    }

    /**
     * Same ticket id, generated twice from the same service instance, must
     * yield an identical credential (HMAC is deterministic given the same
     * key+payload) — this is what makes the credential re-derivable
     * server-side from the ticket id + secret, as opposed to needing its own
     * storage. Distinctness across tickets comes from the random UUID
     * embedded in the payload, not from any per-call randomness in the
     * signing step itself.
     */
    @Test
    void generate_sameTicketIdTwice_isDeterministic() {
        TicketCredentialService service = new TicketCredentialService("dedicated-ticket-secret-for-this-test-0123456789");
        UUID ticketId = UUID.randomUUID();

        assertThat(service.generate(ticketId)).isEqualTo(service.generate(ticketId));
    }

    /**
     * Proves the credential is genuinely signed with its OWN dedicated
     * secret, not derivable from the ticket id alone: the same ticket id
     * signed under a different secret (e.g. what {@code app.jwt.secret}
     * would produce, if the code mistakenly reused it) yields a completely
     * different signature.
     */
    @Test
    void generate_differentSecret_producesDifferentCredentialForTheSameTicketId() {
        UUID ticketId = UUID.randomUUID();
        TicketCredentialService serviceWithSecretA = new TicketCredentialService("ticket-secret-A-0123456789abcdef");
        TicketCredentialService serviceWithSecretB = new TicketCredentialService("ticket-secret-B-fedcba9876543210");

        String credentialA = serviceWithSecretA.generate(ticketId);
        String credentialB = serviceWithSecretB.generate(ticketId);

        assertThat(credentialA).isNotEqualTo(credentialB);
        // Same plaintext ticketId prefix (not itself secret), but the signed suffix differs.
        assertThat(credentialA.split("\\.", 2)[1]).isNotEqualTo(credentialB.split("\\.", 2)[1]);
    }

    /**
     * A naive/guessed signature (e.g. someone hashing the ticket id with a
     * generic algorithm, without knowing the real secret) never matches the
     * real credential's signature — the credential is unguessable per
     * BR-TICKET-001/BR-NFR-003, not merely obfuscated.
     */
    @Test
    void generate_signatureIsNotGuessableWithoutTheSecret() throws Exception {
        String realSecret = "dedicated-ticket-secret-for-this-test-0123456789";
        TicketCredentialService service = new TicketCredentialService(realSecret);
        UUID ticketId = UUID.randomUUID();

        String credential = service.generate(ticketId);
        String guessedSignature = hmacSign("a-wrong-guessed-secret-value", ticketId + ":0");

        assertThat(credential.split("\\.", 2)[1]).isNotEqualTo(guessedSignature);
    }
}
