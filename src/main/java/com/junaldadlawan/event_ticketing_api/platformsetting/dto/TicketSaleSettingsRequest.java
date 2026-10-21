package com.junaldadlawan.event_ticketing_api.platformsetting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/** {@code PUT /platform-settings/ticket-sales} (admins): both values, in minutes; at most 10080 (one week). */
public record TicketSaleSettingsRequest(
        @NotNull
        @Min(0)
        @Max(10080)
        Integer salesCloseMinutesBeforeEvent,

        @NotNull
        @Min(1)
        @Max(10080)
        Integer minSaleDurationMinutes) implements Serializable {
}
