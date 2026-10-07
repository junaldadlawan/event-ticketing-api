package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutGenerateRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PayoutService {

    /** {@code GET /organizations/{orgId}/payouts} - owning owner/organizer or admin. */
    Page<PayoutResponse> list(UUID organizationId, Pageable pageable);

    /**
     * {@code POST /organizations/{orgId}/payouts} - admin only. Settles every order of the organization that is paid
     * (or partly refunded), placed in the period and not yet in a payout. Per order: gross = what the buyer paid minus
     * refunds; fees = the platform fee that order was charged (never more than the gross left); net = gross - fees,
     * which is what the organizer is owed. The payout is SCHEDULED and its orders are marked as settled.
     */
    PayoutResponse generate(UUID organizationId, PayoutGenerateRequest request);
}
