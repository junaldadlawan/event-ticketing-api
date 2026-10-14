package com.junaldadlawan.event_ticketing_api.paymentmethod.service;

import java.util.UUID;

public interface StripeCustomerService {

    /**
     * The Stripe Customer id of the user, created and stored the first time it is needed. Two requests arriving together
     * for a user with none create exactly one Customer. Always the server's own user id: no id from a client is ever used.
     */
    String getOrCreate(UUID userId);
}
