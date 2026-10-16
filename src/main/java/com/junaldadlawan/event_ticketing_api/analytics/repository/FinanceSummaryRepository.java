package com.junaldadlawan.event_ticketing_api.analytics.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Money figures for the admin's finance summary and an organization's income summary, computed live in SQL from {@code orders},
 * {@code refunds} and {@code payouts} (there is no balance or ledger table). Every figure is worked out per order with the same rules the
 * payout uses, so a summary always agrees with the payouts it leads to:
 * <ul>
 *   <li>{@code left} = what the order still holds after its completed refunds (never below 0);</li>
 *   <li>the platform fee kept is 0 when the platform bears it, else the fee capped at {@code left};</li>
 *   <li>the gateway fee is what the gateway really kept when known (read from its own payment record), else the order's applied fee (the foreign
 *       fee for a foreign card), else its quoted fee; an order with none of them has an unknown fee, counted as 0 and flagged;</li>
 *   <li>what comes off the organizer is {@code min(platform fee + gateway deduction, left)}, the gateway deduction being nothing when the
 *       platform bears the fee, the fee the customer paid when the customer bears it, and the applied fee when the organization bears it;</li>
 *   <li>the organizer's share is {@code left} minus that: exactly the net a payout would pay;</li>
 *   <li>the platform bears what the gateway kept that nobody else covered: the fee minus what the customer paid on top and minus what comes off
 *       the organization (so all of it when the platform is the payer or no payer was recorded, and a negative amount, a surplus, when the customer
 *       was charged more than the gateway kept), and the whole fee on a fully refunded order (the gateway keeps its fee and the customer got theirs back).</li>
 * </ul>
 * Only orders placed in {@code [from, to)} with status PAID, PARTIALLY_REFUNDED or REFUNDED in the given currency count; refunds are the
 * completed ones of those orders, whenever they were issued. {@code organizationId} null means every organization.
 */
@Repository
@RequiredArgsConstructor
public class FinanceSummaryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** All amounts in minor units. */
    public record Figures(
            long orders, long refundedOrders, long foreignCardOrders, long unknownFeeOrders,
            long gross, long refunds,
            long platformFeeKept, long platformFeeDeducted, long gatewayFeeDeducted,
            long gatewayCharged, long gatewayPaidByCustomer, long gatewayBorneByOrganization, long gatewayBorneByPlatform,
            long share, long paidOutShare, long inPayoutsShare, long owedShare) {
    }

    public record PayoutTotal(long count, long net) {
    }

    private static final String PER_ORDER = """
            select
              o.total_amount as total, o.payout_id, p.status as payout_status,
              o.gateway_fee_card_origin as origin, o.gateway_fee_payer as gfp, o.platform_fee_payer as pfp,
              o.gateway_fee_amount as gfa, o.platform_fee_amount as pfa,
              o.gateway_fee_actual_amount as gactual,
              case when o.gateway_fee_actual_amount is not null then o.gateway_fee_actual_amount
                   when o.gateway_fee_payer is null then 0
                   else coalesce(o.gateway_fee_applied_amount, o.gateway_fee_amount) end as gcharged,
              coalesce(o.gateway_fee_applied_amount, o.gateway_fee_amount) as gdeduct,
              coalesce((select sum(r.amount_amount) from refunds r where r.order_id = o.id and r.status = 'COMPLETED'), 0) as refunded
            from orders o
            left join payouts p on p.id = o.payout_id
            where o.status in ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')
              and o.total_currency = :currency and o.created_at >= :from and o.created_at < :to
            """;

    public Figures figures(Instant from, Instant to, String currency, UUID organizationId) {
        String orgFilter = organizationId == null ? "" : " and o.payee_type = 'ORGANIZATION' and o.payee_id = :org ";
        // the derived columns of one order, then the sums over them
        String sql = """
                select
                  count(*) as orders,
                  count(*) filter (where refunded > 0) as refunded_orders,
                  count(*) filter (where origin = 'FOREIGN') as foreign_orders,
                  count(*) filter (where gactual is null and gfp is null) as unknown_fee_orders,
                  coalesce(sum(total), 0) as gross,
                  coalesce(sum(refunded), 0) as refunds,
                  coalesce(sum(platform_kept), 0) as platform_kept,
                  coalesce(sum(least(platform_deduction, remaining)), 0) as platform_deducted,
                  coalesce(sum(least(platform_deduction + gateway_deduction, remaining) - least(platform_deduction, remaining)), 0) as gateway_deducted,
                  coalesce(sum(gcharged), 0) as gateway_charged,
                  coalesce(sum(case when gfp = 'CUSTOMER' then gfa else 0 end), 0) as gateway_customer,
                  coalesce(sum(case when gfp = 'ORGANIZATION'
                                    then least(platform_deduction + gateway_deduction, remaining) - least(platform_deduction, remaining) else 0 end), 0) as gateway_org,
                  coalesce(sum(case when total > 0 and refunded >= total then gcharged
                                    else gcharged - (case when gfp = 'CUSTOMER' then gfa else 0 end)
                                         - (case when gfp = 'ORGANIZATION'
                                                 then least(platform_deduction + gateway_deduction, remaining) - least(platform_deduction, remaining)
                                                 else 0 end) end), 0) as gateway_platform,
                  coalesce(sum(remaining - least(platform_deduction + gateway_deduction, remaining)), 0) as share,
                  coalesce(sum(case when payout_status = 'PAID' then remaining - least(platform_deduction + gateway_deduction, remaining) else 0 end), 0) as paid_share,
                  coalesce(sum(case when payout_id is not null and payout_status <> 'PAID' then remaining - least(platform_deduction + gateway_deduction, remaining) else 0 end), 0) as in_payouts_share,
                  coalesce(sum(case when payout_id is null then remaining - least(platform_deduction + gateway_deduction, remaining) else 0 end), 0) as owed_share
                from (
                  select base.*,
                    greatest(total - refunded, 0) as remaining,
                    case when pfp = 'PLATFORM' then 0 else pfa end as platform_deduction,
                    case when pfp = 'PLATFORM' then 0 else least(pfa, greatest(total - refunded, 0)) end as platform_kept,
                    case when gfp is null or gfp = 'PLATFORM' then 0
                         when gfp = 'ORGANIZATION' then gdeduct else gfa end as gateway_deduction
                  from (""" + PER_ORDER + orgFilter + """
                  ) base
                ) per_order
                """;
        Map<String, Object> row = jdbc.queryForMap(sql, params(from, to, currency, organizationId));
        return new Figures(l(row, "orders"), l(row, "refunded_orders"), l(row, "foreign_orders"), l(row, "unknown_fee_orders"), l(row, "gross"), l(row, "refunds"),
                l(row, "platform_kept"), l(row, "platform_deducted"), l(row, "gateway_deducted"),
                l(row, "gateway_charged"), l(row, "gateway_customer"), l(row, "gateway_org"), l(row, "gateway_platform"),
                l(row, "share"), l(row, "paid_share"), l(row, "in_payouts_share"), l(row, "owed_share"));
    }

    /** Payouts created in the period, by status (SCHEDULED, PROCESSING, PAID, FAILED); a status with none is missing from the map. */
    public Map<String, PayoutTotal> payoutsByStatus(Instant from, Instant to, String currency, UUID organizationId) {
        String orgFilter = organizationId == null ? "" : " and organization_id = :org ";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select status, count(*) as c, coalesce(sum(net_amount), 0) as net from payouts
                where net_currency = :currency and created_at >= :from and created_at < :to """ + orgFilter + " group by status",
                params(from, to, currency, organizationId));
        Map<String, PayoutTotal> totals = new HashMap<>();
        for (Map<String, Object> row : rows) {
            totals.put((String) row.get("status"), new PayoutTotal(l(row, "c"), l(row, "net")));
        }
        return totals;
    }

    /** Currencies other than {@code currency} that have orders in the period: left out of the figures, listed so nothing is hidden. */
    public List<String> otherCurrencies(Instant from, Instant to, String currency, UUID organizationId) {
        String orgFilter = organizationId == null ? "" : " and payee_type = 'ORGANIZATION' and payee_id = :org ";
        return jdbc.queryForList("""
                select distinct total_currency from orders
                where status in ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED') and total_currency <> :currency
                  and created_at >= :from and created_at < :to """ + orgFilter + " order by total_currency",
                params(from, to, currency, organizationId), String.class);
    }

    private static MapSqlParameterSource params(Instant from, Instant to, String currency, UUID organizationId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to)).addValue("currency", currency);
        if (organizationId != null) {
            params.addValue("org", organizationId);
        }
        return params;
    }

    private static long l(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0L : ((Number) value).longValue();
    }
}
