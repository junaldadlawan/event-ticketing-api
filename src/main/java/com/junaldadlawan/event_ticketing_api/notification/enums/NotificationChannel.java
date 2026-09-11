package com.junaldadlawan.event_ticketing_api.notification.enums;

/** Only EMAIL is actually implemented (requirements.md §4.12: "email at minimum"); SMS/PUSH are modeled for a future channel. */
public enum NotificationChannel {
    EMAIL,
    SMS,
    PUSH
}
