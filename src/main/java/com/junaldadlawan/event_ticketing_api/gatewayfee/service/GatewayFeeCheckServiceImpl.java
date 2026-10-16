package com.junaldadlawan.event_ticketing_api.gatewayfee.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeCheckResponse;
import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeCheckResponse.Configured;
import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeCheckResponse.Sample;
import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeCheckResponse.SuggestedSetting;
import com.junaldadlawan.event_ticketing_api.gatewayfee.entity.GatewayFeeSetting;
import com.junaldadlawan.event_ticketing_api.gatewayfee.gateway.PaymentFeeSampler;
import com.junaldadlawan.event_ticketing_api.gatewayfee.gateway.PaymentFeeSampler.PaymentSample;
import com.junaldadlawan.event_ticketing_api.gatewayfee.repository.GatewayFeeSettingRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GatewayFeeCheckServiceImpl implements GatewayFeeCheckService {

    /** What PayMongo charged each card payment, VAT included: matched by the fee taken, since PayMongo publishes no rate endpoint. */
    private record KnownRate(String name, BigDecimal percentage, long flat) {
    }

    private static final KnownRate DOMESTIC = new KnownRate("DOMESTIC_CARD", new BigDecimal("3.5"), 1500);
    private static final KnownRate INTERNATIONAL = new KnownRate("INTERNATIONAL_CARD", new BigDecimal("4.5"), 1500);
    private static final int MAX_LIMIT = 20;

    private final GatewayFeeSettingRepository settingRepository;
    private final PaymentFeeSampler sampler;
    private final OrganizationAccessGuard accessGuard;

    @Override
    @Transactional(readOnly = true)
    public GatewayFeeCheckResponse check(int limit) {
        accessGuard.requireAdmin();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        GatewayFeeSetting setting = settingRepository.findById(GatewayFeeSetting.ROW_ID).orElseGet(GatewayFeeSetting::new);
        Configured configured = new Configured(setting.getPercentage(), new MoneyDto(setting.getFlatAmount(), setting.getFlatCurrency()), setting.getPayer(),
                setting.getForeignPercentage(), setting.getForeignPercentage() == null && setting.getForeignFlatAmount() == null ? null
                        : new MoneyDto(setting.getForeignFlatAmount() == null ? 0 : setting.getForeignFlatAmount(), setting.getFlatCurrency()));

        List<Sample> samples = new ArrayList<>();
        SuggestedSetting suggested = null;
        for (PaymentSample payment : sampler.recentPaidPayments(limit)) {
            Sample sample = sample(payment, setting);
            samples.add(sample);
            if (suggested == null && sample.matchedRate() != null) {
                KnownRate rate = sample.matchedRate().equals(INTERNATIONAL.name()) ? INTERNATIONAL : DOMESTIC;
                suggested = new SuggestedSetting(rate.name(), rate.percentage(), new MoneyDto(rate.flat(), payment.currency()));
            }
        }
        boolean allMatch = !samples.isEmpty() && samples.stream().allMatch(Sample::matchesConfigured);
        String note = samples.isEmpty()
                ? ("MOCK".equals(sampler.provider()) ? "No payment gateway is connected (mock mode), so there is nothing to compare."
                        : "PayMongo has no paid payments yet, so its fee cannot be read.")
                : null;
        return new GatewayFeeCheckResponse(sampler.provider(), configured, samples, allMatch, suggested, note);
    }

    private static Sample sample(PaymentSample p, GatewayFeeSetting setting) {
        long total = p.fee() + p.foreignFee();
        boolean foreign = p.cardCountry() != null && !p.cardCountry().isBlank() && !"PH".equalsIgnoreCase(p.cardCountry().trim());
        boolean foreignRate = foreign && (setting.getForeignPercentage() != null || setting.getForeignFlatAmount() != null);
        long expected = foreignRate
                ? expectedFee(p.amount(), setting.getForeignPercentage() == null ? BigDecimal.ZERO : setting.getForeignPercentage(),
                        setting.getForeignFlatAmount() == null ? 0 : setting.getForeignFlatAmount())
                : expectedFee(p.amount(), setting.getPercentage(), setting.getFlatAmount());
        String matched = null;
        if (within(total, expectedFee(p.amount(), DOMESTIC.percentage(), DOMESTIC.flat()))) {
            matched = DOMESTIC.name();
        } else if (within(total, expectedFee(p.amount(), INTERNATIONAL.percentage(), INTERNATIONAL.flat()))) {
            matched = INTERNATIONAL.name();
        }
        String currency = p.currency();
        BigDecimal effective = p.amount() == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(p.amount()), 2, RoundingMode.HALF_UP);
        return new Sample(p.paymentId(), new MoneyDto(p.amount(), currency), new MoneyDto(p.fee(), currency), new MoneyDto(p.foreignFee(), currency),
                new MoneyDto(total, currency), new MoneyDto(p.netAmount(), currency), effective, p.sourceType(), p.cardBrand(), p.cardCountry(),
                p.cardCountry() == null || p.cardCountry().isBlank() ? null : (foreign ? "FOREIGN" : "DOMESTIC"),
                p.paidAt() == null ? null : Instant.ofEpochSecond(p.paidAt()), p.livemode(), matched, new MoneyDto(expected, currency),
                new MoneyDto(total - expected, currency), within(total, expected));
    }

    private static long expectedFee(long amount, BigDecimal percentage, long flat) {
        return BigDecimal.valueOf(amount).multiply(percentage).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact() + flat;
    }

    /** Within a centavo: PayMongo and this app may round the half centavo differently. */
    private static boolean within(long actual, long expected) {
        return Math.abs(actual - expected) <= 1;
    }
}
