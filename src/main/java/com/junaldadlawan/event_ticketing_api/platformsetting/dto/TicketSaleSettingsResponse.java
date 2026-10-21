package com.junaldadlawan.event_ticketing_api.platformsetting.dto;

import com.junaldadlawan.event_ticketing_api.platformsetting.entity.TicketSaleSettings;

import java.io.Serializable;
import java.time.Instant;

public record TicketSaleSettingsResponse(
        int salesCloseMinutesBeforeEvent,
        int minSaleDurationMinutes,
        String updatedBy,
        Instant updatedAt) implements Serializable {

    public static TicketSaleSettingsResponse from(TicketSaleSettings settings) {
        return new TicketSaleSettingsResponse(settings.getSalesCloseMinutesBeforeEvent(), settings.getMinSaleDurationMinutes(),
                settings.getUpdatedBy(), settings.getUpdatedAt());
    }
}
