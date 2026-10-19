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
 * completed ones of those orders, whenever they were issued. {@code organizationId} null means every organization; {@code eventId} set keeps
 * only the orders with a ticket for that event (orders carry no event of their own) and the payouts of that event.
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

    /** One line of an organization's transactions: a sale, a completed refund or a payout. Minor units; {@code net} is negative for money going out. */
    public record TransactionRow(UUID id, String type, Instant occurredAt, String description, String eventTitle, UUID orderId,
                                 long gross, long fees, long net, String currency, String status) {
    }

    private static final String PER_ORDER = """
            select
              o.id, o.created_at, o.status, o.total_currency as currency, o.total_amount as total, o.payout_id, p.status as payout_status,
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
              and o.created_at >= :from and o.created_at < :to
            """;

    /** Each order of {@link #PER_ORDER} that passes {@code filter}, with what it has left and its platform and gateway deductions worked out. */
    private static String perOrder(String filter) {
        return """
                select base.*,
                  greatest(total - refunded, 0) as remaining,
                  case when pfp = 'PLATFORM' then 0 else pfa end as platform_deduction,
                  case when pfp = 'PLATFORM' then 0 else least(pfa, greatest(total - refunded, 0)) end as platform_kept,
                  case when gfp is null or gfp = 'PLATFORM' then 0
                       when gfp = 'ORGANIZATION' then gdeduct else gfa end as gateway_deduction
                from (""" + PER_ORDER + filter + ") base";
    }

    /** The order filter (alias {@code o}): the currency when asked, the organization and the event when set. */
    private static String orderFilter(boolean byCurrency, UUID organizationId, UUID eventId) {
        return (byCurrency ? " and o.total_currency = :currency" : "")
                + (organizationId == null ? "" : " and o.payee_type = 'ORGANIZATION' and o.payee_id = :org")
                + (eventId == null ? "" : " and exists (select 1 from tickets t where t.order_id = o.id and t.event_id = :event)");
    }

    private static String payoutFilter(UUID organizationId, UUID eventId) {
        return (organizationId == null ? "" : " and p.organization_id = :org") + (eventId == null ? "" : " and p.event_id = :event");
    }

    public Figures figures(Instant from, Instant to, String currency, UUID organizationId, UUID eventId) {
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
                from (""" + perOrder(orderFilter(true, organizationId, eventId)) + ") per_order";
        Map<String, Object> row = jdbc.queryForMap(sql, params(from, to, currency, organizationId, eventId));
        return new Figures(l(row, "orders"), l(row, "refunded_orders"), l(row, "foreign_orders"), l(row, "unknown_fee_orders"), l(row, "gross"), l(row, "refunds"),
                l(row, "platform_kept"), l(row, "platform_deducted"), l(row, "gateway_deducted"),
                l(row, "gateway_charged"), l(row, "gateway_customer"), l(row, "gateway_org"), l(row, "gateway_platform"),
                l(row, "share"), l(row, "paid_share"), l(row, "in_payouts_share"), l(row, "owed_share"));
    }

    /** Payouts created in the period, by status (SCHEDULED, PROCESSING, PAID, FAILED); a status with none is missing from the map. */
    public Map<String, PayoutTotal> payoutsByStatus(Instant from, Instant to, String currency, UUID organizationId, UUID eventId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select p.status, count(*) as c, coalesce(sum(p.net_amount), 0) as net from payouts p
                where p.net_currency = :currency and p.created_at >= :from and p.created_at < :to """ + payoutFilter(organizationId, eventId)
                        + " group by p.status",
                params(from, to, currency, organizationId, eventId));
        Map<String, PayoutTotal> totals = new HashMap<>();
        for (Map<String, Object> row : rows) {
            totals.put((String) row.get("status"), new PayoutTotal(l(row, "c"), l(row, "net")));
        }
        return totals;
    }

    /** Currencies other than {@code currency} that have orders in the period: left out of the figures, listed so nothing is hidden. */
    public List<String> otherCurrencies(Instant from, Instant to, String currency, UUID organizationId, UUID eventId) {
        return jdbc.queryForList("""
                select distinct o.total_currency from orders o
                where o.status in ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED') and o.total_currency <> :currency
                  and o.created_at >= :from and o.created_at < :to """ + orderFilter(false, organizationId, eventId) + " order by o.total_currency",
                params(from, to, currency, organizationId, eventId), String.class);
    }

    /**
     * The organizer's share (its income, the same figure as {@code Figures.share}) of the orders placed in the period, by the order's UTC day or
     * month: {@code periodFormat} is a Postgres {@code to_char} pattern ({@code YYYY-MM-DD} or {@code YYYY-MM}). A period with no orders is missing.
     */
    public Map<String, Long> incomeByPeriod(Instant from, Instant to, String currency, UUID organizationId, UUID eventId, String periodFormat) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select to_char(created_at at time zone 'UTC', :format) as period,"
                        + " coalesce(sum(remaining - least(platform_deduction + gateway_deduction, remaining)), 0) as income"
                        + " from (" + perOrder(orderFilter(true, organizationId, eventId)) + ") per_order group by 1",
                params(from, to, currency, organizationId, eventId).addValue("format", periodFormat));
        Map<String, Long> income = new HashMap<>();
        rows.forEach(row -> income.put((String) row.get("period"), l(row, "income")));
        return income;
    }

    /**
     * An organization's money lines in the period, newest first, in every currency: each order placed (SALE: what the buyer paid, the platform and
     * gateway deductions, and the rest), each completed refund issued (REFUND: the amount, going out) and each payout created and not cancelled
     * (PAYOUT: its gross, fees and net, going out; a cancelled payout moved no money). A sale's fees are worked out before refunds; a refund line carries no fees.
     */
    public List<TransactionRow> transactions(Instant from, Instant to, UUID organizationId, UUID eventId, long offset, int limit) {
        return jdbc.query("select * from (" + transactionLines(organizationId, eventId) + ") x order by occurred_at desc, id desc limit :limit offset :offset",
                params(from, to, null, organizationId, eventId).addValue("limit", limit).addValue("offset", offset),
                (rs, i) -> new TransactionRow((UUID) rs.getObject("id"), rs.getString("type"), rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("description"), rs.getString("event_title"), (UUID) rs.getObject("order_id"),
                        rs.getLong("gross"), rs.getLong("fees"), rs.getLong("net"), rs.getString("currency"), rs.getString("status")));
    }

    public long countTransactions(Instant from, Instant to, UUID organizationId, UUID eventId) {
        Long count = jdbc.queryForObject("select count(*) from (" + transactionLines(organizationId, eventId) + ") x",
                params(from, to, null, organizationId, eventId), Long.class);
        return count == null ? 0 : count;
    }

    private static String transactionLines(UUID organizationId, UUID eventId) {
        return """
                select s.id, 'SALE' as type, s.created_at as occurred_at, 'Ticket sale' as description,
                  (select e.title from events e where e.id = (select t.event_id from tickets t where t.order_id = s.id limit 1)) as event_title,
                  s.id as order_id, s.total as gross, least(s.platform_deduction + s.gateway_deduction, s.total) as fees,
                  s.total - least(s.platform_deduction + s.gateway_deduction, s.total) as net, s.currency, s.status
                from (""" + perOrder(orderFilter(false, organizationId, eventId)) + """
                ) s
                union all
                select r.id, 'REFUND', r.created_at, 'Refund',
                  (select e.title from events e where e.id = (select t.event_id from tickets t where t.order_id = o.id limit 1)),
                  o.id, r.amount_amount, 0, -r.amount_amount, r.amount_currency, r.status
                from refunds r join orders o on o.id = r.order_id
                where r.status = 'COMPLETED' and r.created_at >= :from and r.created_at < :to""" + orderFilter(false, organizationId, eventId) + """

                union all
                select p.id, 'PAYOUT', p.created_at, 'Payout', (select e.title from events e where e.id = p.event_id),
                  null::uuid, p.gross_amount, p.fees_amount, -p.net_amount, p.net_currency, p.status
                from payouts p
                where p.status <> 'CANCELLED' and p.created_at >= :from and p.created_at < :to""" + payoutFilter(organizationId, eventId);
    }

    private static MapSqlParameterSource params(Instant from, Instant to, String currency, UUID organizationId, UUID eventId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to)).addValue("currency", currency);
        if (organizationId != null) {
            params.addValue("org", organizationId);
        }
        if (eventId != null) {
            params.addValue("event", eventId);
        }
        return params;
    }

    private static long l(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0L : ((Number) value).longValue();
    }
}
