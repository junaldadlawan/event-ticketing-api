package com.junaldadlawan.event_ticketing_api.payout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/payouts/batch}: the lines ticked on the batch payout screen, each an organization and one of its events, and the
 * UTC days (both included) whose orders are settled. One payout is created per line.
 */
public record PayoutBatchRequest(
        @NotNull
        LocalDate periodStart,

        @NotNull
        LocalDate periodEnd,

        @NotEmpty
        @Valid
        List<Line> items) implements Serializable {

    public record Line(
            @NotNull
            UUID organizationId,

            @NotNull
            UUID eventId) implements Serializable {
    }
}
