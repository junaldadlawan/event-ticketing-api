package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** The UTC days a finance summary covers, both included: {@code from} 00:00 up to the end of {@code to}. Defaults to the last 30 days. */
public record FinancePeriod(LocalDate from, LocalDate to) {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    public static FinancePeriod resolve(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BadRequestException("from must not be after to");
        }
        if (start.plusDays(MAX_DAYS - 1L).isBefore(end)) {   // both ends are included, so 366 days means start + 365
            throw new BadRequestException("The period must not be longer than " + MAX_DAYS + " days");
        }
        return new FinancePeriod(start, end);
    }

    public Instant fromInstant() {
        return from.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /** Exclusive upper bound: the start of the day after {@code to}. */
    public Instant toInstant() {
        return to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
