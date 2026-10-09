package com.junaldadlawan.event_ticketing_api.promocode.enums;

/**
 * Whether a promo code can currently be used: {@code ACTIVE} (the normal state - its validity window, usage
 * limits and ticket types still apply) or {@code PAUSED} (the organizer switched it off: it can't be applied to a
 * cart and a cart already holding it can't check out). Resuming makes it usable again; orders that already used
 * it are never touched.
 */
public enum PromoCodeStatus {
    ACTIVE,
    PAUSED
}
