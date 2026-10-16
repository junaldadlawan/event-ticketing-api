package com.junaldadlawan.event_ticketing_api.order.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Remembers for a few minutes that PayMongo refused card vaulting, so the next checkouts that ask to save a card do not ask PayMongo again
 * (they pay without saving). In memory and per instance: a restart or another instance simply asks once more.
 */
@Component
public class CardVaultingStatus {

    private static final Duration REMEMBER_FOR = Duration.ofMinutes(5);

    private volatile Instant unavailableUntil;

    public boolean unavailable() {
        Instant until = unavailableUntil;
        return until != null && Instant.now().isBefore(until);
    }

    public void markUnavailable() {
        unavailableUntil = Instant.now().plus(REMEMBER_FOR);
    }

    /** Forgets what was learned (for tests, and after the account is changed). */
    public void reset() {
        unavailableUntil = null;
    }
}
