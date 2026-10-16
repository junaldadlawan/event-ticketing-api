package com.junaldadlawan.event_ticketing_api.payout.security;

import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM for the bank account numbers organizations are paid out to: the transfer needs the full number, so it is stored
 * encrypted instead of hashed. The key is {@code PAYOUT_ENCRYPTION_KEY}, 32 random bytes in base64 (for example
 * {@code openssl rand -base64 32}); the application refuses to start with a key of any other length, and without a key
 * saving or reading an account number answers 503. Output is base64(iv + ciphertext + tag), a fresh IV every time.
 */
@Component
public class AccountNumberCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AccountNumberCipher(@Value("${payouts.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            return;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PAYOUT_ENCRYPTION_KEY must be base64");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("PAYOUT_ENCRYPTION_KEY must be 32 bytes (base64 of 32 random bytes)");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plain) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = Arrays.copyOf(iv, IV_BYTES + encrypted.length);
            System.arraycopy(encrypted, 0, out, IV_BYTES, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the account number", e);
        }
    }

    public String decrypt(String encoded) {
        requireKey();
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES), java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt the account number (wrong PAYOUT_ENCRYPTION_KEY?)", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Payout account encryption is not configured (PAYOUT_ENCRYPTION_KEY)");
        }
    }
}
