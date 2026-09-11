package com.junaldadlawan.event_ticketing_api.checkin.security;

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
 * A {@code ScannerDevice}'s credential (Phase 10, {@code deviceAuth} in
 * openapi.yaml) - same "dedicated HMAC-signed opaque token, deliberately
 * re-derivable rather than separately stored" design as {@code
 * TicketCredentialService} (Phase 6a), using its own dedicated secret
 * ({@code app.device.credential.secret}). Format: {@code deviceId + "." +
 * base64url(HMAC-SHA256(deviceId, secret))}.
 * <p>
 * No {@code version} concept here (unlike the ticket credential) - a
 * device's credential is invalidated by flipping {@code ScannerDevice.status}
 * to {@code REVOKED} and checking that live at request time ({@code
 * DeviceAuthenticationFilter}), not by rotating the signed payload.
 */
@Component
public class ScannerDeviceCredentialService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec keySpec;

    public ScannerDeviceCredentialService(@Value("${app.device.credential.secret}") String secret) {
        this.keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String generate(UUID deviceId) {
        String payload = deviceId.toString();
        return payload + "." + sign(payload);
    }

    /** Returns the verified device id, or {@link Optional#empty()} if the credential is malformed or forged. */
    public Optional<UUID> verify(String rawCredential) {
        if (rawCredential == null) {
            return Optional.empty();
        }
        String[] parts = rawCredential.split("\\.", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        String payload = parts[0];
        UUID deviceId;
        try {
            deviceId = UUID.fromString(payload);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        String expectedSignature = sign(payload);
        boolean signatureMatches = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8));
        return signatureMatches ? Optional.of(deviceId) : Optional.empty();
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to sign device credential", e);
        }
    }
}
