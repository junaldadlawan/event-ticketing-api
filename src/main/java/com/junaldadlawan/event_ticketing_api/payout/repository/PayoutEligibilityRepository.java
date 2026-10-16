package com.junaldadlawan.event_ticketing_api.payout.repository;

import com.junaldadlawan.event_ticketing_api.payout.enums.EventScope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The lines of the batch payout screen: for each approved organization and event, what a payout would pay right now. A line is the orders that
 * are due (PAID or PARTIALLY_REFUNDED, in no payout yet, placed in the period) of one event of the organization. The amount is worked out per
 * order exactly as {@code PayoutServiceImpl} and {@code FinanceSummaryRepository} do (gross after completed refunds minus the platform fee and
 * the gateway deduction that come off the organizer, never more than the gross), so the amount shown is the net the payout will pay.
 */
@Repository
@RequiredArgsConstructor
public class PayoutEligibilityRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** {@code currencies} is how many currencies the line's orders use (more than one cannot be paid in one payout). */
    public record Line(UUID organizationId, String organizationName, UUID eventId, String eventTitle, String currency, long currencies,
                       long eligible, boolean pendingRefund) {
    }

    public List<Line> lines(Instant from, Instant to, UUID organizationId, EventScope scope) {
        String orgFilter = organizationId == null ? "" : " and o.payee_id = :org ";
        String scopeFilter = scope == EventScope.COMPLETED
                ? " and (e.status = 'COMPLETED' or (e.end_time <= now() and e.status not in ('CANCELLED', 'SUSPENDED', 'DRAFT'))) " : "";
        String sql = """
                select org_id, org_name, event_id, event_title, min(currency) as currency, count(distinct currency) as currencies,
                       sum(remaining - least(platform_deduction + gateway_deduction, remaining)) as eligible,
                       bool_or(pending_refund) as pending_refund
                from (
                  select o.payee_id as org_id, org.name as org_name, e.id as event_id, e.title as event_title, e.start_time as starts,
                         o.total_currency as currency,
                         greatest(o.total_amount - refunded.amount, 0) as remaining,
                         case when o.platform_fee_payer = 'PLATFORM' then 0 else o.platform_fee_amount end as platform_deduction,
                         case when o.gateway_fee_payer is null or o.gateway_fee_payer = 'PLATFORM' then 0
                              when o.gateway_fee_payer = 'ORGANIZATION' then coalesce(o.gateway_fee_applied_amount, o.gateway_fee_amount)
                              else o.gateway_fee_amount end as gateway_deduction,
                         exists (select 1 from refunds r where r.order_id = o.id and r.status = 'PENDING') as pending_refund
                  from orders o
                  join organizations org on org.id = o.payee_id and org.status = 'APPROVED' and org.deleted_at is null
                  join events e on e.id = (select t.event_id from tickets t where t.order_id = o.id limit 1)
                  cross join lateral (select coalesce(sum(r.amount_amount), 0) as amount from refunds r
                                      where r.order_id = o.id and r.status = 'COMPLETED') refunded
                  where o.payee_type = 'ORGANIZATION' and o.payout_id is null and o.status in ('PAID', 'PARTIALLY_REFUNDED')
                    and o.created_at >= :from and o.created_at < :to """ + orgFilter + scopeFilter + """
                ) per_order
                group by org_id, org_name, event_id, event_title, starts
                having sum(remaining - least(platform_deduction + gateway_deduction, remaining)) > 0
                order by org_name, starts, event_title
                """;
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to));
        if (organizationId != null) {
            params.addValue("org", organizationId);
        }
        return jdbc.query(sql, params, (rs, i) -> new Line((UUID) rs.getObject("org_id"), rs.getString("org_name"), (UUID) rs.getObject("event_id"),
                rs.getString("event_title"), rs.getString("currency"), rs.getLong("currencies"), rs.getLong("eligible"), rs.getBoolean("pending_refund")));
    }
}
