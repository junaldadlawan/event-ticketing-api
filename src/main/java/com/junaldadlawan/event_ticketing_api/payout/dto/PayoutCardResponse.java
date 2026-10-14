package com.junaldadlawan.event_ticketing_api.payout.dto;

import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutCard;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** A payout card as shown. The gateway token is never part of it. */
public record PayoutCardResponse(
        UUID id,
        UUID organizationId,
        String holderName,
        String brand,
        String last4,
        int expMonth,
        int expYear,
        boolean primary,
        Instant createdAt) implements Serializable {

    public static PayoutCardResponse from(PayoutCard card) {
        return new PayoutCardResponse(card.getId(), card.getOrganizationId(), card.getHolderName(), card.getBrand(),
                card.getLast4(), card.getExpMonth(), card.getExpYear(), card.isPrimary(), card.getCreatedAt());
    }
}
