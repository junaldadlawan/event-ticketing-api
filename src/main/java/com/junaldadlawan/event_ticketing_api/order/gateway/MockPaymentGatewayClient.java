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

    /**
     * Deterministic for testability, same shape as {@link #charge}: a
     * {@code gatewayRef} containing {@code "fail"} always fails (there's no
     * natural analogue to a "declined card" for a refund reversal, so this
     * is the hook tests use to exercise the failure path - see
     * {@code Payment.gatewayRef}, which is otherwise never organizer/buyer
     * controlled).
     */
    @Override
    public PaymentResult refund(String gatewayRef, Money amount) {
        if (gatewayRef != null && gatewayRef.contains("fail")) {
            return PaymentResult.failure("Refund declined by gateway for gatewayRef " + gatewayRef);
        }
        return PaymentResult.success("mock_refund_" + UUID.randomUUID());
    }
}
