package com.junaldadlawan.event_ticketing_api.analytics.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One organization's own money for the orders of its events placed in a period (its owner or an admin), in one currency. It never shows the
 * platform's income or costs. {@code sales.gross} is what buyers paid, fees added on top included; {@code deductions} is everything between
 * that and the organization's income: the platform fee and the gateway fee that came or will come off, including fees customers paid on top
 * (which are not the organization's money); {@code income} = gross - refunds - deductions, exactly the net its payouts pay. {@code paidOut}
 * is the part of {@code income} in PAID payouts, {@code inPayouts} the part in scheduled, processing or failed ones, and
 * {@code owedNotYetInPayout} the part not in any payout yet. {@code payouts} counts the payouts created in the period by status.
 */
public record OrganizationFinanceResponse(
        UUID organizationId,
        LocalDate from,
        LocalDate to,
        String currency,
        List<String> otherCurrencies,
        long orders,
        Sales sales,
        Deductions deductions,
        MoneyDto income,
        MoneyDto paidOut,
        MoneyDto inPayouts,
        MoneyDto owedNotYetInPayout,
        FinanceSummaryResponse.Payouts payouts) implements Serializable {

    public record Sales(MoneyDto gross, MoneyDto refunds, MoneyDto net) implements Serializable {
    }

    public record Deductions(MoneyDto platformFee, MoneyDto gatewayFee, MoneyDto total) implements Serializable {
    }
}
