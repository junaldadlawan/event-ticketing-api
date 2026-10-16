package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutAccountRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutAccountResponse;

import java.util.UUID;

public interface PayoutAccountService {

    /** The organization's payout account. Owner, organizer or admin. 404 when it has none. */
    PayoutAccountResponse get(UUID organizationId);

    /** Sets or replaces the organization's payout account. Owner or admin; the organization must be approved. */
    PayoutAccountResponse set(UUID organizationId, PayoutAccountRequest request);

    /** Soft-deletes the account. Owner or admin. Payouts already sent are not affected. 404 when there is none. */
    void remove(UUID organizationId);
}
