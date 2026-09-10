package com.junaldadlawan.event_ticketing_api.order.gateway;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deterministic, no-external-call mock payment gateway (confirmed decision:
 * a real Stripe/PayPal/etc integration is explicitly out of scope for this
 * dispatch). No API keys, no HTTP calls - this is intentionally a
 * swappable-later stub.
 * <p>
 * Deterministic behavior for testability: a {@code paymentMethodToken}
 * starting with {@code "tok_fail"} always fails; anything else always
 * succeeds with a fake {@code gatewayRef}.
 */
@Component
public class MockPaymentGatewayClient implements PaymentGatewayClient {

    private static final String FAILING_TOKEN_PREFIX = "tok_fail";

    @Override
    public PaymentResult charge(String paymentMethodToken, Money amount) {
        if (paymentMethodToken != null && paymentMethodToken.startsWith(FAILING_TOKEN_PREFIX)) {
            return PaymentResult.failure("Payment declined by gateway for the provided payment method token");
        }
        return PaymentResult.success("mock_" + UUID.randomUUID());
    }
}
