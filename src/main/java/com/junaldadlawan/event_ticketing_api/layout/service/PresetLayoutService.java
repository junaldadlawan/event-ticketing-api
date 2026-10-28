package com.junaldadlawan.event_ticketing_api.layout.service;

import com.junaldadlawan.event_ticketing_api.layout.dto.PresetLayoutRequest;
import com.junaldadlawan.event_ticketing_api.layout.dto.PresetLayoutResponse;
import com.junaldadlawan.event_ticketing_api.layout.dto.PresetLayoutSummary;
import com.junaldadlawan.event_ticketing_api.layout.entity.PresetLayout;

import java.util.List;
import java.util.UUID;

/** Preset layouts: admins manage them; owners and organizers of any organization read the active ones. */
public interface PresetLayoutService {

    /** The active presets; an admin also sees the inactive ones. */
    List<PresetLayoutSummary> list();

    PresetLayoutResponse get(UUID presetId);

    /** The preset as its reader may see it (404 for an inactive one unless admin). */
    PresetLayout getReadable(UUID presetId);

    PresetLayoutResponse create(PresetLayoutRequest request);

    PresetLayoutResponse replace(UUID presetId, PresetLayoutRequest request);

    void delete(UUID presetId);
}
