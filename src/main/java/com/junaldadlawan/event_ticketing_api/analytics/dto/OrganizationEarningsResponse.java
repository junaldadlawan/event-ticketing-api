package com.junaldadlawan.event_ticketing_api.analytics.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An organization's income (the same figure as {@code OrganizationFinanceResponse.income}: net sales minus the platform and gateway fees) per UTC
 * day ({@code 2026-10-15}) or month ({@code 2026-10}) of the orders placed in the period, in one currency. Every period of the range is present,
 * with 0 when it had none. {@code eventId}, when set, narrows it to that one event.
 */
public record OrganizationEarningsResponse(
        Interval interval,
        LocalDate from,
        LocalDate to,
        String currency,
        UUID eventId,
        List<Point> points) implements Serializable {

    public enum Interval { DAY, MONTH }

    public record Point(String period, MoneyDto earnings) implements Serializable {
    }
}
