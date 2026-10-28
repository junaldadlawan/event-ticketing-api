package com.junaldadlawan.event_ticketing_api.layout.service;

import com.junaldadlawan.event_ticketing_api.layout.dto.SeatLayoutRequest;
import com.junaldadlawan.event_ticketing_api.layout.dto.SeatLayoutResponse;
import com.junaldadlawan.event_ticketing_api.layout.dto.SeatLayoutSummary;

import java.util.List;
import java.util.UUID;

/**
 * An organization's seat layouts. Members of the organization and admins read them; the owner, or an organizer holding EVENTS, writes them
 * (admins are read-only).
 */
public interface SeatLayoutService {

    List<SeatLayoutSummary> list(UUID orgId);

    SeatLayoutResponse create(UUID orgId, SeatLayoutRequest request);

    SeatLayoutResponse get(UUID layoutId);

    /** Replaces the whole layout and adds one to its version. */
    SeatLayoutResponse replace(UUID layoutId, SeatLayoutRequest request);

    /** Soft delete; 409 while an upcoming event still uses the layout. */
    void delete(UUID layoutId);

    /** Copies an active preset into the organization as an independent layout under the preset's name. */
    SeatLayoutResponse copyPreset(UUID orgId, UUID presetId);
}
