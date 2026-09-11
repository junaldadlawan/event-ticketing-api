package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PayoutService {

    /** {@code GET /organizations/{orgId}/payouts} - owning owner/organizer or admin. */
    Page<PayoutResponse> list(UUID organizationId, Pageable pageable);
}
