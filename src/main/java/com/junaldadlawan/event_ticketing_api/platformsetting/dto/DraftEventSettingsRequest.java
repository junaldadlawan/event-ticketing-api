package com.junaldadlawan.event_ticketing_api.platformsetting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/** {@code PUT /platform-settings/draft-events} (admins): minutes before an event's start at which an unpublished draft is deleted; 0 = off; at most 10080 (one week). */
public record DraftEventSettingsRequest(
        @NotNull
        @Min(0)
        @Max(10080)
        Integer autoDeleteMinutesBeforeStart) implements Serializable {
}
