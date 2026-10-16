package com.junaldadlawan.event_ticketing_api.payout.dto;

import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutAccount;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** A payout account as shown: only the last 4 digits of the number, never the whole of it. */
public record PayoutAccountResponse(
        UUID id,
        UUID organizationId,
        String bankName,
        String bankCode,
        String accountName,
        String accountNumberLast4,
        Instant createdAt,
        Instant updatedAt) implements Serializable {

    public static PayoutAccountResponse from(PayoutAccount account) {
        return new PayoutAccountResponse(account.getId(), account.getOrganizationId(), account.getBankName(), account.getBankCode(),
                account.getAccountName(), account.getAccountNumberLast4(), account.getCreatedAt(), account.getUpdatedAt());
    }
}
