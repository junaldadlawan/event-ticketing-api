package com.junaldadlawan.event_ticketing_api.region.dto;

import com.junaldadlawan.event_ticketing_api.region.entity.SupportedCountry;
import com.junaldadlawan.event_ticketing_api.region.entity.SupportedCurrency;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** {@code GET /platform/regions}: the enabled currencies and countries, for every country and currency picker. */
public record RegionsResponse(
        String baseCurrency,
        List<Currency> currencies,
        List<Country> countries) implements Serializable {

    public record Currency(String code, String name, String symbol, int minorUnits, BigDecimal usdRate, Instant rateUpdatedAt,
                           boolean rateStale) implements Serializable {
        public static Currency from(SupportedCurrency c) {
            return new Currency(c.getCode(), c.getName(), c.getSymbol(), c.getMinorUnits(), c.getUsdRate(), c.getRateUpdatedAt(), c.isRateStale());
        }
    }

    public record Country(String code, String name, String currencyCode) implements Serializable {
        public static Country from(SupportedCountry c) {
            return new Country(c.getCode(), c.getName(), c.getCurrencyCode());
        }
    }

    public static RegionsResponse from(List<SupportedCurrency> currencies, List<SupportedCountry> countries) {
        return new RegionsResponse(SupportedCurrency.REFERENCE, currencies.stream().map(Currency::from).toList(),
                countries.stream().map(Country::from).toList());
    }
}
