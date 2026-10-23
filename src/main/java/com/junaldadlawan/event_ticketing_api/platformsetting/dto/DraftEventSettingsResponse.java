package com.junaldadlawan.event_ticketing_api.platformsetting.dto;

import com.junaldadlawan.event_ticketing_api.platformsetting.entity.DraftEventSettings;

import java.io.Serializable;
import java.time.Instant;

public record DraftEventSettingsResponse(
        int autoDeleteMinutesBeforeStart,
        String updatedBy,
        Instant updatedAt) implements Serializable {

    public static DraftEventSettingsResponse from(DraftEventSettings settings) {
        return new DraftEventSettingsResponse(settings.getAutoDeleteMinutesBeforeStart(), settings.getUpdatedBy(), settings.getUpdatedAt());
    }
}
