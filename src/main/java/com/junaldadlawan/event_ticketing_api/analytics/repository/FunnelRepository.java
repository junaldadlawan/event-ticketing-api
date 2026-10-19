package com.junaldadlawan.event_ticketing_api.analytics.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The event funnel log (BR-ANALYTICS-003): {@code event_views} and {@code cart_activity}, both append-only and written and read in plain SQL.
 * Days are grouped in the event's time zone ({@code tz}, an IANA name such as {@code Asia/Manila}).
 */
@Repository
@RequiredArgsConstructor
public class FunnelRepository {

    public enum CartAction { ADD, REMOVE, EXPIRE, CHECKOUT }

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Records a view unless the same visitor already has one for this event since {@code dedupSince}. Returns whether a row was written.
     * shortcut: two requests of one visitor in the same instant can both pass the check; upgrade to a unique bucket key if that ever matters.
     */
    public boolean insertViewUnlessRecent(UUID eventId, String visitorKey, UUID userId, Instant at, Instant dedupSince) {
        return jdbc.update("""
                insert into event_views (id, event_id, visitor_key, user_id, viewed_at)
                select :id, :event, :visitor, :user, :at
                where not exists (select 1 from event_views where event_id = :event and visitor_key = :visitor and viewed_at > :since)
                """, new MapSqlParameterSource("id", UUID.randomUUID()).addValue("event", eventId).addValue("visitor", visitorKey)
                .addValue("user", userId).addValue("at", Timestamp.from(at)).addValue("since", Timestamp.from(dedupSince))) == 1;
    }

    /** One cart line change; the event comes from the ticket type and the buyer from the cart. */
    public void insertCartActivity(UUID cartId, UUID cartItemId, UUID ticketTypeId, int quantity, CartAction action, Instant at) {
        jdbc.update("""
                insert into cart_activity (id, event_id, ticket_type_id, cart_item_id, buyer_id, quantity, action, occurred_at)
                select :id, t.event_id, t.id, :item, c.buyer_id, :quantity, :action, :at
                from ticket_types t, carts c where t.id = :type and c.id = :cart
                """, new MapSqlParameterSource("id", UUID.randomUUID()).addValue("item", cartItemId).addValue("quantity", quantity)
                .addValue("action", action.name()).addValue("at", Timestamp.from(at)).addValue("type", ticketTypeId).addValue("cart", cartId));
    }

    /** Views and distinct visitors in {@code [from, to)}, in all and per local day. */
    public record ViewTotals(long views, long uniqueVisitors) {
    }

    public ViewTotals viewTotals(UUID eventId, Instant from, Instant to) {
        Map<String, Object> row = jdbc.queryForMap("""
                select count(*) as views, count(distinct visitor_key) as visitors from event_views
                where event_id = :event and viewed_at >= :from and viewed_at < :to
                """, range(eventId, from, to, null));
        return new ViewTotals(l(row, "views"), l(row, "visitors"));
    }

    public record DayViews(String day, long views, long uniqueVisitors) {
    }

    public List<DayViews> viewsByDay(UUID eventId, Instant from, Instant to, String tz) {
        return jdbc.query("""
                select to_char(viewed_at at time zone :tz, 'YYYY-MM-DD') as day, count(*) as views, count(distinct visitor_key) as visitors
                from event_views where event_id = :event and viewed_at >= :from and viewed_at < :to group by 1
                """, range(eventId, from, to, tz), (rs, i) -> new DayViews(rs.getString("day"), rs.getLong("views"), rs.getLong("visitors")));
    }

    /** Per ticket type and action: how many rows, how many tickets, how many distinct buyers. */
    public record ActivityTotal(UUID ticketTypeId, String action, long actions, long quantity, long uniqueBuyers) {
    }

    public List<ActivityTotal> activityByTicketType(UUID eventId, Instant from, Instant to) {
        return jdbc.query("""
                select ticket_type_id, action, count(*) as actions, sum(quantity) as quantity, count(distinct buyer_id) as buyers
                from cart_activity where event_id = :event and occurred_at >= :from and occurred_at < :to group by 1, 2
                """, range(eventId, from, to, null), (rs, i) -> new ActivityTotal((UUID) rs.getObject("ticket_type_id"), rs.getString("action"),
                rs.getLong("actions"), rs.getLong("quantity"), rs.getLong("buyers")));
    }

    /** Distinct buyers over the whole event (not summed per ticket type, where one buyer could count twice). */
    public long uniqueBuyers(UUID eventId, Instant from, Instant to, CartAction action) {
        Long buyers = jdbc.queryForObject("""
                select count(distinct buyer_id) from cart_activity
                where event_id = :event and action = :action and occurred_at >= :from and occurred_at < :to
                """, range(eventId, from, to, null).addValue("action", action.name()), Long.class);
        return buyers == null ? 0 : buyers;
    }

    public record DayActivity(String day, String action, long quantity) {
    }

    public List<DayActivity> activityByDay(UUID eventId, Instant from, Instant to, String tz) {
        return jdbc.query("""
                select to_char(occurred_at at time zone :tz, 'YYYY-MM-DD') as day, action, sum(quantity) as quantity
                from cart_activity where event_id = :event and occurred_at >= :from and occurred_at < :to and action in ('ADD', 'CHECKOUT')
                group by 1, 2
                """, range(eventId, from, to, tz), (rs, i) -> new DayActivity(rs.getString("day"), rs.getString("action"), rs.getLong("quantity")));
    }

    /** When this log started: the moment the V57 migration ran on this database (null if Flyway has no record of it). */
    public Instant trackingSince() {
        List<Timestamp> installed = jdbc.queryForList("select installed_on from flyway_schema_history where version = '57' and success",
                new MapSqlParameterSource(), Timestamp.class);
        return installed.isEmpty() ? null : installed.get(0).toInstant();
    }

    /** Retention: removes every row older than {@code before}; returns how many. */
    public int deleteOlderThan(Instant before) {
        MapSqlParameterSource params = new MapSqlParameterSource("before", Timestamp.from(before));
        return jdbc.update("delete from event_views where viewed_at < :before", params)
                + jdbc.update("delete from cart_activity where occurred_at < :before", params);
    }

    private static MapSqlParameterSource range(UUID eventId, Instant from, Instant to, String tz) {
        return new MapSqlParameterSource("event", eventId).addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to)).addValue("tz", tz);
    }

    private static long l(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0L : ((Number) value).longValue();
    }
}
