package com.junaldadlawan.event_ticketing_api.region.service;

import com.junaldadlawan.event_ticketing_api.region.dto.CountryResponse;
import com.junaldadlawan.event_ticketing_api.region.dto.CurrencyResponse;
import com.junaldadlawan.event_ticketing_api.region.dto.RegionsResponse;

import java.util.List;
import java.util.UUID;

/** Admin-managed platform currencies (USD is the reference) and countries. */
public interface RegionService {

    /** Admin: enables a currency and fetches its rate at once. 400 unknown code, 409 already enabled or no rate in the feed, 502 feed down. */
    CurrencyResponse enableCurrency(String code);

    /** Admin: 409 for USD, or while an enabled country, an organization or an event that hasn't ended uses it. */
    void disableCurrency(String code);

    /** Admin: re-fetches every enabled currency's rate now. */
    List<CurrencyResponse> refreshRates();

    /** The daily job's refresh (no signed-in caller). A failed fetch keeps the last rates and marks them stale. */
    void refreshRatesBySystem();

    /** Admin: enables a country or changes its currency. 400 unknown country or currency not enabled. */
    CountryResponse putCountry(String code, String currencyCode);

    /** Admin: 409 while an organization, or the venue of an upcoming event, is in it. */
    void disableCountry(String code);

    /** Public: the enabled currencies and countries. */
    RegionsResponse regions();

    /** The upper-cased code of an enabled country, or 400. */
    String requireEnabledCountry(String code);

    /** The currency of an enabled country (an organization gets it when approved), or 400. */
    String currencyOfCountry(String code);

    /** 400 unless {@code currency} is the organization's currency; nothing to check before the organization has one (not approved yet). */
    void requireOrganizationCurrency(UUID organizationId, String currency);
}
