package com.junaldadlawan.event_ticketing_api.region.dto;

import com.junaldadlawan.event_ticketing_api.region.entity.SupportedCurrency;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/** A supported currency; {@code usdRate} = units of it per 1 USD (null until the feed has given one). */
public record CurrencyResponse(
        String code,
        String name,
        String symbol,
        int minorUnits,
        boolean enabled,
        BigDecimal usdRate,
        Instant rateUpdatedAt,
        boolean rateStale) implements Serializable {

    public static CurrencyResponse from(SupportedCurrency currency) {
        return new CurrencyResponse(currency.getCode(), currency.getName(), currency.getSymbol(), currency.getMinorUnits(), currency.isEnabled(),
                currency.getUsdRate(), currency.getRateUpdatedAt(), currency.isRateStale());
    }
}
