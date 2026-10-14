package com.junaldadlawan.event_ticketing_api.paymentmethod.dto;

import java.io.Serializable;

/** The client secret the web app hands to Stripe.js to collect a card. It is a secret for that one setup: do not log it. */
public record SetupIntentResponse(String clientSecret) implements Serializable {
}
