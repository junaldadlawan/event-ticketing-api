package com.junaldadlawan.event_ticketing_api.region.dto;

import com.junaldadlawan.event_ticketing_api.region.entity.SupportedCountry;

import java.io.Serializable;

public record CountryResponse(
        String code,
        String name,
        String currencyCode,
        boolean enabled) implements Serializable {

    public static CountryResponse from(SupportedCountry country) {
        return new CountryResponse(country.getCode(), country.getName(), country.getCurrencyCode(), country.isEnabled());
    }
}
