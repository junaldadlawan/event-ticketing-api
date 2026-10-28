package com.junaldadlawan.event_ticketing_api.analytics.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The share counters of an event: the running totals on {@code events}, the per-day row in {@code share_daily_counts} and the per-hour row in
 * {@code share_hourly_counts} (the day and hour are the event's own). Each is changed only by an atomic {@code column = column + 1} (an upsert for
 * the rows), never read-modify-write, so concurrent calls never lose a count. The column names come from {@link Counter}, never from a caller.
 */
@Repository
@RequiredArgsConstructor
public class ShareCounterRepository {

    /** What is counted: the total's column on {@code events} and the column of the daily row. */
    public enum Counter {
        SHARE_BUTTON("share_click_count", "share_button"),
        COPY_LINK("share_copy_count", "copy_link"),
        QR_GENERATE("share_qr_count", "qr_generate"),
        LINK_OPEN("share_link_open_count", "link_open"),
        QR_SCAN("share_qr_scan_count", "qr_scan"),
        LINK_BUY("share_link_buy_count", "link_buy"),
        QR_BUY("share_qr_buy_count", "qr_buy");

        private final String column;
        private final String dailyColumn;

        Counter(String column, String dailyColumn) {
            this.column = column;
            this.dailyColumn = dailyColumn;
        }
    }

    /** One day of the timeline. */
    public record Day(LocalDate day, long shareButton, long copyLink, long qrGenerate, long linkOpen, long qrScan, long linkBuy, long qrBuy) {
    }

    /** One hour of the day, summed over a range of days. */
    public record Hour(int hour, long shareButton, long copyLink, long qrGenerate, long linkOpen, long qrScan, long linkBuy, long qrBuy) {
    }

    private final JdbcTemplate jdbc;

    /** Counts once for a live event in one of {@code statuses} (stored as text); an unknown, deleted or other event counts nothing. */
    @Transactional
    public void incrementIfVisible(UUID eventId, Counter counter, List<String> statuses) {
        String marks = String.join(",", Collections.nCopies(statuses.size(), "?"));
        Object[] args = new Object[statuses.size() + 1];
        args[0] = eventId;
        for (int i = 0; i < statuses.size(); i++) {
            args[i + 1] = statuses.get(i);
        }
        List<String> zones = jdbc.queryForList("select timezone from events where id = ? and deleted_at is null and status in (" + marks + ")", String.class, args);
        if (!zones.isEmpty()) {
            bump(eventId, counter, zones.get(0));
        }
    }

    /** Counts once for an event already known to be visible. */
    @Transactional
    public void increment(UUID eventId, Counter counter) {
        jdbc.queryForList("select timezone from events where id = ?", String.class, eventId).stream().findFirst()
                .ifPresent(zone -> bump(eventId, counter, zone));
    }

    /** The first day with a row for the event, or null when nothing was counted yet. */
    public LocalDate firstDay(UUID eventId) {
        return jdbc.queryForObject("select min(day) from share_daily_counts where event_id = ?", (rs, i) -> rs.getObject(1, LocalDate.class), eventId);
    }

    /** The rows of the days {@code from}..{@code to} (both included) that have one, oldest first. */
    public List<Day> days(UUID eventId, LocalDate from, LocalDate to) {
        return jdbc.query("select day, share_button, copy_link, qr_generate, link_open, qr_scan, link_buy, qr_buy from share_daily_counts"
                        + " where event_id = ? and day >= ? and day <= ? order by day",
                (rs, i) -> new Day(rs.getObject("day", LocalDate.class), rs.getLong("share_button"), rs.getLong("copy_link"), rs.getLong("qr_generate"),
                        rs.getLong("link_open"), rs.getLong("qr_scan"), rs.getLong("link_buy"), rs.getLong("qr_buy")),
                eventId, from, to);
    }

    /** The hours 0-23 summed over the days {@code from}..{@code to} (both included); only the hours that have a row, in order. */
    public List<Hour> hours(UUID eventId, LocalDate from, LocalDate to) {
        return jdbc.query("select hour, sum(share_button) share_button, sum(copy_link) copy_link, sum(qr_generate) qr_generate, sum(link_open) link_open,"
                        + " sum(qr_scan) qr_scan, sum(link_buy) link_buy, sum(qr_buy) qr_buy from share_hourly_counts"
                        + " where event_id = ? and day >= ? and day <= ? group by hour order by hour",
                (rs, i) -> new Hour(rs.getInt("hour"), rs.getLong("share_button"), rs.getLong("copy_link"), rs.getLong("qr_generate"),
                        rs.getLong("link_open"), rs.getLong("qr_scan"), rs.getLong("link_buy"), rs.getLong("qr_buy")),
                eventId, from, to);
    }

    private void bump(UUID eventId, Counter counter, String timezone) {
        jdbc.update("update events set " + counter.column + " = " + counter.column + " + 1 where id = ?", eventId);
        String column = counter.dailyColumn;
        ZonedDateTime now = ZonedDateTime.now(zoneOf(timezone));   // one reading, so the day and the hour agree
        jdbc.update("insert into share_daily_counts (event_id, day, " + column + ") values (?, ?, 1)"
                + " on conflict (event_id, day) do update set " + column + " = share_daily_counts." + column + " + 1", eventId, now.toLocalDate());
        jdbc.update("insert into share_hourly_counts (event_id, day, hour, " + column + ") values (?, ?, ?, 1)"
                + " on conflict (event_id, day, hour) do update set " + column + " = share_hourly_counts." + column + " + 1",
                eventId, now.toLocalDate(), now.getHour());
    }

    private static ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? "UTC" : timezone);
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }
}
