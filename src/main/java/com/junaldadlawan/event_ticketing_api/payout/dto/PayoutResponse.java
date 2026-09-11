package com.junaldadlawan.event_ticketing_api.payout.dto;

import com.junaldadlawan.event_ticketing_api.payout.entity.Payout;
import com.junaldadlawan.event_ticketing_api.payout.enums.PayoutStatus;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** DTO for {@link Payout}, matching openapi.yaml's {@code Payout} schema. */
public record PayoutResponse(
        UUID id,
        UUID organizationId,
        MoneyDto gross,
        MoneyDto fees,
        MoneyDto net,
        LocalDate periodStart,
        LocalDate periodEnd,
        PayoutStatus status,
        Instant createdAt,
        Instant updatedAt) implements Serializable {

    public static PayoutResponse from(Payout payout) {
        return new PayoutResponse(
                payout.getId(),
                payout.getOrganizationId(),
                new MoneyDto(payout.getGross().getAmount(), payout.getGross().getCurrency()),
                new MoneyDto(payout.getFees().getAmount(), payout.getFees().getCurrency()),
                new MoneyDto(payout.getNet().getAmount(), payout.getNet().getCurrency()),
                payout.getPeriodStart(),
                payout.getPeriodEnd(),
                payout.getStatus(),
                payout.getCreatedAt(),
                payout.getUpdatedAt());
    }
}
