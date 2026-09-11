package com.junaldadlawan.event_ticketing_api.notification.gateway;

/**
 * Delivery abstraction for the {@code email} channel (requirements.md
 * §4.12: "email at minimum"). Same swappable-later-stub shape as {@code
 * PaymentGatewayClient} - a real SMTP/provider integration is out of scope
 * for this dispatch.
 */
public interface EmailSender {

    /** @return true if delivery succeeded. Never throws for a delivery failure - only for a genuinely unexpected error. */
    boolean send(String to, String subject, String body);
}
