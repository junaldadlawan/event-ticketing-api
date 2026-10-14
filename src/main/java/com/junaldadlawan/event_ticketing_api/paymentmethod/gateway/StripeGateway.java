package com.junaldadlawan.event_ticketing_api.paymentmethod.gateway;

import java.util.UUID;

/**
 * The few Stripe calls saved cards need, behind an interface so the rest of the code never talks to Stripe directly.
 * {@code payments.gateway=stripe} uses {@link StripeApiGateway}; the default {@code mock} uses {@link MockStripeGateway},
 * which makes no network call at all.
 */
public interface StripeGateway {

    /** True when real Stripe calls are made (so a Stripe Customer and a {@code pm_...} id are needed). */
    boolean live();

    /** Creates a Stripe Customer. The idempotency key makes a repeated call return the same Customer. */
    String createCustomer(String email, String name, UUID userId, String idempotencyKey);

    /** Creates a SetupIntent (usage off_session) for the customer and returns its client secret. */
    String createSetupIntent(String customerId);

    /** Makes sure the payment method is attached to the customer and returns what Stripe says the card is. */
    CardDetails attachAndDescribe(String customerId, String paymentMethodId);

    /** Detaches the payment method from its customer. */
    void detach(String paymentMethodId);

    record CardDetails(String brand, String last4, int expMonth, int expYear) {
    }
}
