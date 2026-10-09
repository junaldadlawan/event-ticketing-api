package com.junaldadlawan.event_ticketing_api.tickettemplate.enums;

/**
 * What a text field on a ticket template prints. <b>Dynamic</b> keys differ per
 * ticket (so their box is reserved space the value fills); <b>static</b> keys are
 * the same on every ticket of the event; {@link #CUSTOM} prints the organizer's
 * own text. Every key except CUSTOM may appear once per template, CUSTOM up to
 * ten times.
 */
public enum TextFieldKey {

    // differs per ticket
    TICKET_TYPE(true),
    SECTION(true),
    ROW(true),
    SEAT(true),
    TICKET_NUMBER(true),
    ATTENDEE_NAME(true),

    // same on every ticket of the event
    EVENT_NAME(false),
    EVENT_DATE(false),
    EVENT_TIME(false),
    VENUE(false),

    CUSTOM(false);

    private final boolean dynamic;

    TextFieldKey(boolean dynamic) {
        this.dynamic = dynamic;
    }

    public boolean isDynamic() {
        return dynamic;
    }
}
