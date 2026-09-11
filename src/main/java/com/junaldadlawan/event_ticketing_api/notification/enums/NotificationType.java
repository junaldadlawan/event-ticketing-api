package com.junaldadlawan.event_ticketing_api.notification.enums;

/** BR-NOTIFY-001's full list. Not every value has a wired trigger yet — see the roadmap for which. */
public enum NotificationType {
    ORDER_CONFIRMATION,
    PAYMENT_RECEIPT,
    EVENT_REMINDER,
    EVENT_CHANGE,
    EVENT_CANCELLATION,
    WAITLIST_AVAILABILITY,
    REFUND_CONFIRMATION
}
