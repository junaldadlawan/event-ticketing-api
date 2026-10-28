package com.junaldadlawan.event_ticketing_api.layout.dto;

import com.junaldadlawan.event_ticketing_api.layout.entity.PresetLayout;

import java.io.Serializable;
import java.util.UUID;

/** A row of {@code GET /layout-presets}. */
public record PresetLayoutSummary(UUID id, String name, String description, boolean active, double width, double height, int seatCount, int version)
        implements Serializable {

    public static PresetLayoutSummary from(PresetLayout preset) {
        return new PresetLayoutSummary(preset.getId(), preset.getName(), preset.getDescription(), preset.isActive(), preset.getWidth(), preset.getHeight(),
                preset.getSeatCount(), preset.getVersion());
    }
}
