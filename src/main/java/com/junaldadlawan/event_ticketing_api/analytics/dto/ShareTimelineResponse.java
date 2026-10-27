package com.junaldadlawan.event_ticketing_api.analytics.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * The share counters of an event per day, for a line chart. {@code days} has every day of the range in the event's time zone, zeros included.
 * {@code trackingSince} is the first day that has any count (null when nothing was counted yet): totals from before the daily history started
 * have no dates, so the days before it are zeros, not an estimate. {@code byHour} always has the 24 hours; counts from before the hourly history
 * started have no hour, so they are left out of it (not spread).
 */
public record ShareTimelineResponse(String timezone, LocalDate trackingSince, List<Day> days, List<Hour> byHour) implements Serializable {

    /** One hour of the day (0-23, in the event's time zone), summed over the requested days. */
    public record Hour(int hour, long shareButton, long copyLink, long qrGenerate, long linkOpen, long qrScan, long linkBuy, long qrBuy)
            implements Serializable {
    }

    public record Day(LocalDate day, long shareButton, long copyLink, long qrGenerate, long linkOpen, long qrScan, long linkBuy, long qrBuy)
            implements Serializable {
    }
}
