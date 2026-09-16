package com.junaldadlawan.event_ticketing_api.dispute.service;

import com.junaldadlawan.event_ticketing_api.dispute.dto.DisputeCreateRequest;
import com.junaldadlawan.event_ticketing_api.dispute.dto.DisputeResponse;
import com.junaldadlawan.event_ticketing_api.dispute.dto.DisputeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.dispute.enums.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DisputeService {

    /** {@code POST /disputes} (BR-ADMIN-003) - caller must be the order's buyer/ticket's owner, or admin. */
    DisputeResponse create(DisputeCreateRequest request);

    /** {@code GET /disputes?status=} - admin only. */
    Page<DisputeResponse> list(DisputeStatus statusFilter, Pageable pageable);

    /** {@code GET /disputes/{disputeId}} - the user who raised it, or admin. */
    DisputeResponse get(UUID disputeId);

    /** {@code PATCH /disputes/{disputeId}} - admin only; rejects an already-resolved/dismissed dispute. */
    DisputeResponse update(UUID disputeId, DisputeUpdateRequest request);
}
