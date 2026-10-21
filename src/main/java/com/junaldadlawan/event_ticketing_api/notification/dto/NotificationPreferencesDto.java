package com.junaldadlawan.event_ticketing_api.notification.dto;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Map;

/**
 * {@code GET / PUT /users/me/notification-preferences}: type name -> on/off. The GET lists every type a user may turn
 * off; a PUT may send only some of them (the rest stay as they are). Unknown type names are a 400.
 */
public record NotificationPreferencesDto(
        @NotNull
        Map<String, Boolean> preferences) implements Serializable {
}
