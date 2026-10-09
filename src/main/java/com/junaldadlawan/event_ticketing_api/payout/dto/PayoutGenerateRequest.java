package com.junaldadlawan.event_ticketing_api.payout.dto;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Body of {@code POST /api/v1/organizations/{orgId}/payouts}: the orders placed from the start of {@code periodStart}
 * to the end of {@code periodEnd} (UTC, both days included). {@code periodEnd} may not be in the future.
 */
public record PayoutGenerateRequest(
        @NotNull
        LocalDate periodStart,

        @NotNull
        LocalDate periodEnd) implements Serializable {
}
