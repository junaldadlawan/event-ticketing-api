package com.junaldadlawan.event_ticketing_api.order.enums;

/** Where a checkout that waits for the payment provider stands. An order exists only in {@link #PAID}. */
public enum PendingCheckoutStatus {
    /** The payment intent exists; the buyer is entering the card or authenticating (3-D Secure). */
    PENDING,
    /** Payment confirmed by the provider; the order and tickets were issued. */
    PAID,
    /** The provider reported the payment failed; nothing was charged and the cart is untouched. */
    FAILED,
    /** The payment succeeded but the order could not be issued (hold expired, amount changed): it was refunded. */
    REFUNDED
}
