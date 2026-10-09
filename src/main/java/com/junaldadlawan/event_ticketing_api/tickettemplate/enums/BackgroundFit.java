package com.junaldadlawan.event_ticketing_api.tickettemplate.enums;

/**
 * How a ticket template's background image is placed on the ticket. {@code COVER}
 * (the default when a template has no fit) scales it to fill the ticket and
 * centre-crops; {@code CONTAIN} scales it to fit inside, centred, with the
 * background colour showing around it; {@code STRETCH} is exactly the ticket's
 * size; {@code CUSTOM} places it in the rectangle given by
 * {@code backgroundX/Y/Width/Height}.
 */
public enum BackgroundFit {
    COVER,
    CONTAIN,
    STRETCH,
    CUSTOM
}
