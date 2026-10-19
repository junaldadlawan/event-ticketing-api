package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.payout.dto.EligiblePayoutsResponse;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutBatchRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutGenerateRequest;
import com.junaldadlawan.event_ticketing_api.payout.enums.EventScope;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutResponse;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutWalletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
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

    /**
     * {@code GET /payouts/eligible} - admin only. What can be paid out now, one line per approved organization and event: the orders due
     * (paid or partly refunded, in no payout yet) placed in the UTC days {@code periodStart}..{@code periodEnd} (both included, not in the
     * future, at most 366 days), the net a payout would pay for them, and READY or ON_HOLD (a pending refund on any order, or orders in more than
     * one currency). {@code scope} COMPLETED (default) keeps events that are over; ALL keeps every event. {@code organizationId} narrows to one.
     */
    EligiblePayoutsResponse eligible(LocalDate periodStart, LocalDate periodEnd, UUID organizationId, EventScope scope);

    /**
     * {@code POST /payouts/batch} - admin only. One payout per chosen line (organization + event), each settling that event's due orders of the
     * period. All or nothing: if any line has nothing due, is on hold (pending refund), mixes currencies or its organization has no payout
     * destination, nothing is created (409 naming the line). 400 for an empty or duplicated list or a bad period, 404 for an unknown
     * organization or an event that is not the organization's.
     */
    List<PayoutResponse> generateBatch(PayoutBatchRequest request);

    /**
     * {@code POST /payouts/{id}/send} - admin only. Sends the net amount of a SCHEDULED or FAILED payout to the organization's payout
     * account through the payment provider. The payout becomes PROCESSING and ends PAID or FAILED when the provider reports it (webhook or
     * {@link #refresh}); with PayMongo a transfer can stay pending for up to a banking day. A failed payout can be sent again.
     */
    PayoutResponse send(UUID payoutId);

    /**
     * {@code POST /payouts/{id}/cancel} - admin only. Cancels a SCHEDULED payout and releases its orders (they are owed and eligible again),
     * in one transaction. 404 for an unknown payout; 409 for any other status, including one a concurrent send already moved to PROCESSING.
     */
    PayoutResponse cancel(UUID payoutId);

    /** {@code GET /payout-wallet} - admin only. The wallet payouts are sent from: status, balance, limits, usage and account. */
    PayoutWalletResponse wallet();

    /** {@code POST /payouts/{id}/refresh} - admin only. Asks the provider how a PROCESSING payout's transfer stands and settles it. */
    PayoutResponse refresh(UUID payoutId);

    /**
     * The provider told us (signed webhook) that a transfer ended. Unknown transfers and payouts that are not PROCESSING are ignored, so
     * a repeated event changes nothing.
     */
    void onTransferResult(String transferId, boolean succeeded, String failureReason);
}
