package com.junaldadlawan.event_ticketing_api.analytics.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.LocalDate;

/** One day's worth of {@link EventAnalyticsResponse#salesOverTime()}. */
public record SalesOverTimeEntry(LocalDate date, int ticketsSold, MoneyDto revenue) implements Serializable {
}
