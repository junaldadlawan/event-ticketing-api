package com.junaldadlawan.event_ticketing_api.notification.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deterministic, no-external-call mock email sender (confirmed decision: a
 * real SMTP/provider integration is explicitly out of scope for this
 * dispatch, same reasoning as {@code MockPaymentGatewayClient}). No API
 * keys, no network calls - just logs, so delivery is observable in tests
 * and locally without any external dependency.
 * <p>
 * Deterministic behavior for testability, same shape as {@code
 * MockPaymentGatewayClient}: a recipient address containing {@code
 * "fail-delivery"} always fails; anything else always succeeds.
 */
@Slf4j
@Component
public class MockEmailSender implements EmailSender {

    private static final String FAILING_RECIPIENT_MARKER = "fail-delivery";

    @Override
    public boolean send(String to, String subject, String body) {
        if (to != null && to.contains(FAILING_RECIPIENT_MARKER)) {
            log.warn("Mock email delivery failed for {}: {}", to, subject);
            return false;
        }
        log.info("Mock email sent to {}: {}", to, subject);
        return true;
    }
}
