package com.junaldadlawan.event_ticketing_api.refund.service;

import com.junaldadlawan.event_ticketing_api.refund.dto.RefundCreateRequest;
import com.junaldadlawan.event_ticketing_api.refund.dto.RefundResponse;

import java.util.List;
import java.util.UUID;

public interface RefundService {

    /** {@code POST /orders/{orderId}/refunds} - owning organizer or admin only (BR-PAY-002), subject to the event's refund policy (BR-PAY-003). */
    RefundResponse createRefund(UUID orderId, RefundCreateRequest request);

    /** {@code GET /orders/{orderId}/refunds} - owning buyer, event organizer/owner, or admin. */
    List<RefundResponse> listRefunds(UUID orderId);

    /**
     * BR-PAY-005: cancelling an event must trigger an automated refund for
     * every ticket holder, regardless of the event's configured refund
     * policy - called by {@code EventServiceImpl.cancelEvent}, not exposed
     * via any controller of its own. Best-effort per order: one order's
     * gateway failure doesn't stop the rest from being attempted.
     */
    void refundAllForEventCancellation(UUID eventId, UUID initiatedBy);
}
