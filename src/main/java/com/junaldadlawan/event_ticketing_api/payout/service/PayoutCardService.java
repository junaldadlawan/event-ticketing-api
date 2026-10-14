package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutCardRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutCardResponse;

import java.util.List;
import java.util.UUID;

public interface PayoutCardService {

    /** The organization's cards, oldest first. Owner, organizer or admin. */
    List<PayoutCardResponse> list(UUID organizationId);

    /** Adds a card. Owner or admin; the organization must be approved. The first card is always primary. */
    PayoutCardResponse add(UUID organizationId, PayoutCardRequest request);

    /** Makes the card the organization's primary one, unsetting the previous one. Owner or admin. */
    PayoutCardResponse setPrimary(UUID cardId);

    /** Soft-deletes the card. Owner or admin. The primary card cannot go while other cards exist. */
    void remove(UUID cardId);
}
