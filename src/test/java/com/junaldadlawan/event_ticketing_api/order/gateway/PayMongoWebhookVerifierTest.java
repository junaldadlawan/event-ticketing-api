package com.junaldadlawan.event_ticketing_api.order.gateway;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayMongoWebhookVerifierTest {

    private final PayMongoWebhookVerifier verifier = new PayMongoWebhookVerifier("whsk_test_secret");
    private final String body = "{\"data\":{\"id\":\"evt_1\",\"attributes\":{\"type\":\"payment.paid\"}}}";

    private byte[] bytes() {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private String signed(String timestamp, String payload) {
        return verifier.sign(timestamp + "." + payload);
    }

    @Test
    void aTestModeSignature_isAccepted() {
        assertThat(verifier.verify(bytes(), "t=1700000000,te=" + signed("1700000000", body) + ",li=")).isTrue();
    }

    @Test
    void aLiveModeSignature_isAccepted() {
        assertThat(verifier.verify(bytes(), "t=1700000000,te=,li=" + signed("1700000000", body))).isTrue();
    }

    @Test
    void aTamperedBody_isRejected() {
        String header = "t=1700000000,te=" + signed("1700000000", body) + ",li=";

        assertThat(verifier.verify(body.replace("paid", "failed").getBytes(StandardCharsets.UTF_8), header)).isFalse();
    }

    @Test
    void aSignatureMadeWithAnotherSecret_isRejected() {
        String forged = new PayMongoWebhookVerifier("another_secret").sign("1700000000." + body);

        assertThat(verifier.verify(bytes(), "t=1700000000,te=" + forged + ",li=")).isFalse();
    }

    @Test
    void aChangedTimestamp_isRejected() {
        assertThat(verifier.verify(bytes(), "t=1700000999,te=" + signed("1700000000", body) + ",li=")).isFalse();
    }

    @Test
    void aMissingOrMalformedHeader_isRejected() {
        assertThat(verifier.verify(bytes(), null)).isFalse();
        assertThat(verifier.verify(bytes(), "")).isFalse();
        assertThat(verifier.verify(bytes(), "te=abc")).isFalse();                       // no timestamp
        assertThat(verifier.verify(bytes(), "t=1700000000")).isFalse();                  // no signature
        assertThat(verifier.verify(bytes(), "t=1700000000,te=,li=")).isFalse();          // empty signatures
        assertThat(verifier.verify(bytes(), "garbage")).isFalse();
    }

    @Test
    void theApplicationRefusesToStartWithoutAWebhookSecret() {
        assertThatThrownBy(() -> new PayMongoWebhookVerifier("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PayMongoWebhookVerifier("  ")).isInstanceOf(IllegalStateException.class);
    }
}
