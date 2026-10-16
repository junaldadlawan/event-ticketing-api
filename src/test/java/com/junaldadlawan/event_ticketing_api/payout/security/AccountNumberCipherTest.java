package com.junaldadlawan.event_ticketing_api.payout.security;

import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountNumberCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);   // a test key, never a real one

    @Test
    void roundTrips_andNeverShowsThePlainNumber_andEncryptsDifferentlyEveryTime() {
        AccountNumberCipher cipher = new AccountNumberCipher(KEY);

        String first = cipher.encrypt("1234567890");
        String second = cipher.encrypt("1234567890");

        assertThat(first).doesNotContain("1234567890").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("1234567890");
        assertThat(cipher.decrypt(second)).isEqualTo("1234567890");
    }

    @Test
    void aTamperedValue_orTheWrongKey_isRefused() {
        String encrypted = new AccountNumberCipher(KEY).encrypt("1234567890");
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        bytes[bytes.length - 1] ^= 1;

        assertThatThrownBy(() -> new AccountNumberCipher(KEY).decrypt(Base64.getEncoder().encodeToString(bytes))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AccountNumberCipher(Base64.getEncoder().encodeToString(new byte[32]).replace('A', 'B')).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aBadKeyStopsTheApplication_andNoKeyAnswers503WhenUsed() {
        assertThatThrownBy(() -> new AccountNumberCipher("not base64!")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AccountNumberCipher(Base64.getEncoder().encodeToString(new byte[16]))).isInstanceOf(IllegalStateException.class);

        AccountNumberCipher noKey = new AccountNumberCipher("");
        assertThatThrownBy(() -> noKey.encrypt("1234567890")).isInstanceOf(ApiException.class).hasMessageContaining("PAYOUT_ENCRYPTION_KEY");
        assertThatThrownBy(() -> noKey.decrypt("x")).isInstanceOf(ApiException.class);
    }
}
