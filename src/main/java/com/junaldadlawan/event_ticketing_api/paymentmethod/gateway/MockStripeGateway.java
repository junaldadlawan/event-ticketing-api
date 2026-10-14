package com.junaldadlawan.event_ticketing_api.paymentmethod.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** The default gateway ({@code payments.gateway=mock}): no Stripe call is ever made. */
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "mock", matchIfMissing = true)
public class MockStripeGateway implements StripeGateway {

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public String createCustomer(String email, String name, UUID userId, String idempotencyKey) {
        return "cus_mock_" + userId;
    }

    @Override
    public String createSetupIntent(String customerId) {
        return "seti_mock_secret";
    }

    @Override
    public CardDetails attachAndDescribe(String customerId, String paymentMethodId) {
        throw new UnsupportedOperationException("The mock gateway does not read cards from Stripe");
    }

    @Override
    public void detach(String paymentMethodId) {
        // nothing to detach: a mock card has no Stripe payment method
    }
}
