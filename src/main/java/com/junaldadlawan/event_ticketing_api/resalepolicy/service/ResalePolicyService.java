package com.junaldadlawan.event_ticketing_api.resalepolicy.service;

import com.junaldadlawan.event_ticketing_api.resalepolicy.dto.ResalePolicyResponse;
import com.junaldadlawan.event_ticketing_api.resalepolicy.dto.ResalePolicyUpdateRequest;

import java.util.UUID;

public interface ResalePolicyService {

    /** {@code GET /events/{eventId}/resale-policy} - public. */
    ResalePolicyResponse get(UUID eventId);

    /** {@code PATCH /events/{eventId}/resale-policy} - owning organizer/owner/admin. Upserts. */
    ResalePolicyResponse update(UUID eventId, ResalePolicyUpdateRequest request);
}
