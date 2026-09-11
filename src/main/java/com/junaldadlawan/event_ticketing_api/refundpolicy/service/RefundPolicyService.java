package com.junaldadlawan.event_ticketing_api.refundpolicy.service;

import com.junaldadlawan.event_ticketing_api.refundpolicy.dto.RefundPolicyResponse;
import com.junaldadlawan.event_ticketing_api.refundpolicy.dto.RefundPolicyUpdateRequest;

import java.util.UUID;

public interface RefundPolicyService {

    /** {@code GET /events/{eventId}/refund-policy} - organizer, admin, or a buyer with an order on it. */
    RefundPolicyResponse get(UUID eventId);

    /** {@code PATCH /events/{eventId}/refund-policy} - owning organizer/owner/admin. Upserts. */
    RefundPolicyResponse update(UUID eventId, RefundPolicyUpdateRequest request);
}
