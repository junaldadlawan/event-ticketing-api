package com.junaldadlawan.event_ticketing_api.analytics.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * The platform's money for the orders placed in a period (admin only), in one currency. Computed live from orders, refunds and payouts.
 * <ul>
 *   <li>{@code sales.gross} is what buyers paid (fees added on top included); {@code sales.net} is that minus completed refunds.</li>
 *   <li>{@code platform.feeIncome} is the platform fee kept (none when the platform bore it, and none on a refunded order);
 *       {@code platform.gatewayCostBorne} is the gateway fee the platform itself pays (see {@code gatewayFees});
 *       {@code platform.netIncome} is the first minus the second.</li>
 *   <li>{@code gatewayFees.charged} is what the gateway really kept in all (read from its own payment records; an order whose fee is not known
 *       yet counts 0 and is counted in {@code unknownFeeOrders}); {@code paidByCustomer} and {@code borneByOrganization} are the parts the customer
 *       paid on top and that comes off the organizations; {@code borneByPlatform} is the rest, so the platform pays all of it when it is the payer or
 *       no payer was recorded, and it is negative when the customers were charged more than the gateway kept. The platform also absorbs the whole
 *       fee of a fully refunded order.</li>
 *   <li>{@code organizers.share} is what the organizations are owed in all, the same net their payouts pay: {@code paidOut} (in PAID payouts),
 *       {@code inPayouts} (in scheduled, processing or failed payouts) and {@code owedNotYetInPayout} (not in any payout yet).</li>
 *   <li>{@code payouts} counts the payouts created in the period by status.</li>
 *   <li>{@code otherCurrencies} lists other currencies that had orders in the period, left out of every figure.</li>
 * </ul>
 */
public record FinanceSummaryResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        List<String> otherCurrencies,
        Orders orders,
        Sales sales,
        Platform platform,
        GatewayFees gatewayFees,
        Organizers organizers,
        Payouts payouts) implements Serializable {

    public record Orders(long count, long refunded) implements Serializable {
    }

    public record Sales(MoneyDto gross, MoneyDto refunds, MoneyDto net) implements Serializable {
    }

    public record Platform(MoneyDto feeIncome, MoneyDto gatewayCostBorne, MoneyDto netIncome) implements Serializable {
    }

    public record GatewayFees(MoneyDto charged, MoneyDto paidByCustomer, MoneyDto borneByOrganization, MoneyDto borneByPlatform,
                              long foreignCardOrders, long unknownFeeOrders) implements Serializable {
    }

    public record Organizers(MoneyDto share, MoneyDto paidOut, MoneyDto inPayouts, MoneyDto owedNotYetInPayout) implements Serializable {
    }

    public record PayoutTotal(long count, MoneyDto net) implements Serializable {
    }

    public record Payouts(PayoutTotal scheduled, PayoutTotal processing, PayoutTotal paid, PayoutTotal failed) implements Serializable {
    }
}
