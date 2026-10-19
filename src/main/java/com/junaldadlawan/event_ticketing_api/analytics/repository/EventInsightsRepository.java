package com.junaldadlawan.event_ticketing_api.analytics.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Aggregates over one event's sales, computed in SQL so they stay right however many orders the event has. An event's orders are the PAID and
 * PARTIALLY_REFUNDED orders with a ticket for it (orders carry no event of their own); a live ticket is one that is not REFUNDED or CANCELLED.
 * Nothing here returns a buyer's id or birth date.
 */
@Repository
@RequiredArgsConstructor
public class EventInsightsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** One buyer of the event: the age group at their first order ({@code null} without a birth date) and their live tickets. */
    public record AudienceBuyer(String ageGroup, long tickets) {
    }

    /** The live tickets, orders and promo-code orders placed in one hour of one day, in the event's time zone. */
    public record SalesSlot(String day, int isoWeekday, int hour, long tickets, long orders, long promoOrders) {
    }

    public List<AudienceBuyer> audience(UUID eventId) {
        return jdbc.query("""
                select case when u.birth_date is null then null
                            when age.years < 18 then 'Under 18'
                            when age.years < 25 then '18-24'
                            when age.years < 35 then '25-34'
                            when age.years < 45 then '35-44'
                            when age.years < 55 then '45-54'
                            else '55+' end as age_group,
                       b.tickets
                from (
                  select o.buyer_id, min(o.created_at) as first_order_at,
                         count(t.id) filter (where t.status not in ('REFUNDED', 'CANCELLED')) as tickets
                  from orders o join tickets t on t.order_id = o.id and t.event_id = :event
                  where o.status in ('PAID', 'PARTIALLY_REFUNDED')
                  group by o.buyer_id
                ) b
                left join users u on u.id = b.buyer_id
                left join lateral (select extract(year from age((b.first_order_at at time zone 'UTC')::date, u.birth_date))::int as years) age on true
                """, new MapSqlParameterSource("event", eventId), (rs, i) -> new AudienceBuyer(rs.getString("age_group"), rs.getLong("tickets")));
    }

    /** {@code timezone} is the event's IANA zone, such as {@code Asia/Manila}. */
    public List<SalesSlot> salesSlots(UUID eventId, String timezone) {
        return jdbc.query("""
                select to_char(o.created_at at time zone :tz, 'YYYY-MM-DD') as day,
                       extract(isodow from o.created_at at time zone :tz)::int as dow,
                       extract(hour from o.created_at at time zone :tz)::int as hour,
                       count(t.id) as tickets, count(distinct o.id) as orders,
                       count(distinct o.id) filter (where o.promo_code is not null) as promo_orders
                from orders o join tickets t on t.order_id = o.id and t.event_id = :event and t.status not in ('REFUNDED', 'CANCELLED')
                where o.status in ('PAID', 'PARTIALLY_REFUNDED')
                group by 1, 2, 3
                """, new MapSqlParameterSource("event", eventId).addValue("tz", timezone),
                (rs, i) -> new SalesSlot(rs.getString("day"), rs.getInt("dow"), rs.getInt("hour"), rs.getLong("tickets"), rs.getLong("orders"),
                        rs.getLong("promo_orders")));
    }
}
