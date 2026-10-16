package com.junaldadlawan.event_ticketing_api.gatewayfee.dto;

import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What the payment gateway really charged on its latest paid payments, next to the fee setting. PayMongo publishes no endpoint for its
 * rates, so each sample's rate is recognised from the payment itself: {@code matchedRate} is DOMESTIC_CARD (3.5% + PHP 15.00, VAT included)
 * or INTERNATIONAL_CARD (4.5% + PHP 15.00: the same plus PayMongo's 1% foreign-card surcharge), or null when neither fits within a centavo.
 * {@code cardOrigin} is DOMESTIC for a card issued in the Philippines and FOREIGN for any other country, and decides which configured rate a
 * payment is compared with. {@code suggestedSetting} is the rate of the newest recognised payment, ready to send to {@code PUT /payment-gateway-fee}. The setting is
 * applied to the amount before the fee, while PayMongo's fee is taken from the amount charged, so a customer-paid fee can differ by a few
 * centavos. {@code provider} is MOCK outside PayMongo mode, and then there are no samples.
 */
public record GatewayFeeCheckResponse(
        String provider,
        Configured configured,
        List<Sample> samples,
        boolean allSamplesMatchConfigured,
        SuggestedSetting suggestedSetting,
        String note) implements Serializable {

    /** {@code percentage} and {@code flatAmount} are the domestic rate; the foreign pair is null when a foreign card costs the domestic rate. */
    public record Configured(BigDecimal percentage, MoneyDto flatAmount, GatewayFeePayer payer, BigDecimal foreignPercentage,
                             MoneyDto foreignFlatAmount) implements Serializable {
    }

    public record Sample(
            String paymentId,
            MoneyDto amount,
            MoneyDto fee,
            MoneyDto foreignFee,
            MoneyDto totalFee,
            MoneyDto netAmount,
            BigDecimal effectivePercent,
            String sourceType,
            String cardBrand,
            String cardCountry,
            String cardOrigin,
            Instant paidAt,
            boolean livemode,
            String matchedRate,
            MoneyDto expectedWithConfigured,   // the foreign rate for a foreign card (when one is set), the domestic rate otherwise
            MoneyDto differenceFromConfigured,
            boolean matchesConfigured) implements Serializable {
    }

    public record SuggestedSetting(String basedOn, BigDecimal percentage, MoneyDto flatAmount) implements Serializable {
    }
}
