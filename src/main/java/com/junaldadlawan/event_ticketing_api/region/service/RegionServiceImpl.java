package com.junaldadlawan.event_ticketing_api.region.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.region.dto.CountryResponse;
import com.junaldadlawan.event_ticketing_api.region.dto.CurrencyResponse;
import com.junaldadlawan.event_ticketing_api.region.dto.RegionsResponse;
import com.junaldadlawan.event_ticketing_api.region.entity.SupportedCountry;
import com.junaldadlawan.event_ticketing_api.region.entity.SupportedCurrency;
import com.junaldadlawan.event_ticketing_api.region.gateway.ExchangeRateClient;
import com.junaldadlawan.event_ticketing_api.region.repository.SupportedCountryRepository;
import com.junaldadlawan.event_ticketing_api.region.repository.SupportedCurrencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {

    private static final Set<String> COUNTRY_CODES = Set.of(Locale.getISOCountries());

    private final SupportedCurrencyRepository currencyRepository;
    private final SupportedCountryRepository countryRepository;
    private final OrganizationRepository organizationRepository;
    private final ExchangeRateClient exchangeRateClient;
    private final OrganizationAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    // ---- currencies ----

    @Override
    @Transactional
    public CurrencyResponse enableCurrency(String code) {
        accessGuard.requireAdmin();
        Currency iso = isoCurrency(code);
        SupportedCurrency currency = currencyRepository.findById(iso.getCurrencyCode()).orElse(null);
        if (currency != null && currency.isEnabled()) {
            throw new ConflictException(iso.getCurrencyCode() + " is already enabled");
        }
        BigDecimal rate = fetchRates().get(iso.getCurrencyCode());
        if (rate == null) {
            throw new ConflictException("The exchange-rate feed has no rate for " + iso.getCurrencyCode() + ", so it can't be enabled");
        }
        if (currency == null) {
            currency = SupportedCurrency.builder().code(iso.getCurrencyCode()).build();
        }
        currency.setName(iso.getDisplayName(Locale.ENGLISH));
        currency.setSymbol(symbolOf(iso));
        currency.setMinorUnits((short) iso.getDefaultFractionDigits());
        currency.setEnabled(true);
        setRate(currency, rate);
        SupportedCurrency saved = currencyRepository.save(currency);
        Map<String, Object> details = details("code", saved.getCode(), "name", saved.getName());
        details.put("usdRate", rate.toPlainString());
        details.put("source", ExchangeRateClient.SOURCE);
        auditLogService.recordByCurrentUser("currency.enabled", "Currency", null, null, details);
        return CurrencyResponse.from(saved);
    }

    @Override
    @Transactional
    public void disableCurrency(String code) {
        accessGuard.requireAdmin();
        String upper = upper(code);
        if (SupportedCurrency.REFERENCE.equals(upper)) {
            throw new ConflictException("USD is the reference currency and can't be disabled");
        }
        SupportedCurrency currency = currencyRepository.findById(upper).filter(SupportedCurrency::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Currency " + upper + " is not enabled"));
        if (countryRepository.existsByCurrencyCodeAndEnabledTrue(upper)) {
            throw new ConflictException(upper + " is the currency of an enabled country; change or disable that country first");
        }
        if (currencyRepository.usedByOrganization(upper)) {
            throw new ConflictException(upper + " is the currency of an organization");
        }
        if (currencyRepository.usedByOpenEvent(upper, Instant.now())) {
            throw new ConflictException(upper + " is used by the ticket types of an event that hasn't ended");
        }
        currency.setEnabled(false);
        currencyRepository.save(currency);
        auditLogService.recordByCurrentUser("currency.disabled", "Currency", null, null, details("code", upper, "name", currency.getName()));
    }

    @Override
    @Transactional
    public List<CurrencyResponse> refreshRates() {
        accessGuard.requireAdmin();
        refreshRatesBySystem();
        return currencyRepository.findByEnabledTrueOrderByCode().stream().map(CurrencyResponse::from).toList();
    }

    @Override
    @Transactional
    public void refreshRatesBySystem() {
        Map<String, BigDecimal> rates;
        try {
            rates = exchangeRateClient.usdRates();
        } catch (RuntimeException e) {
            log.warn("Exchange rates could not be fetched; the last rates are kept and marked stale: {}", e.toString());
            rates = Map.of();
        }
        for (SupportedCurrency currency : currencyRepository.findByEnabledTrueOrderByCode()) {
            if (SupportedCurrency.REFERENCE.equals(currency.getCode())) {
                continue;
            }
            BigDecimal rate = rates.get(currency.getCode());
            if (rate == null) {
                currency.setRateStale(true);   // keep the last rate, never make one up
                currencyRepository.save(currency);
                continue;
            }
            setRate(currency, rate);
            currencyRepository.save(currency);
            Map<String, Object> details = details("code", currency.getCode(), "name", currency.getName());
            details.put("usdRate", rate.toPlainString());
            details.put("source", ExchangeRateClient.SOURCE);
            auditLogService.recordByCurrentUser("currency.rate_refreshed", "Currency", null, null, details);
        }
    }

    // ---- countries ----

    @Override
    @Transactional
    public CountryResponse putCountry(String code, String currencyCode) {
        accessGuard.requireAdmin();
        String upper = upper(code);
        if (!COUNTRY_CODES.contains(upper)) {
            throw new BadRequestException("country must be an ISO 3166-1 alpha-2 country code such as PH");
        }
        String currency = upper(currencyCode);
        if (!currencyRepository.findById(currency).map(SupportedCurrency::isEnabled).orElse(false)) {
            throw new BadRequestException("Currency " + currency + " is not enabled; enable it first");
        }
        SupportedCountry country = countryRepository.findById(upper).orElse(null);
        boolean wasEnabled = country != null && country.isEnabled();
        String before = country == null ? null : country.getCurrencyCode();
        if (wasEnabled && currency.equals(before)) {
            return CountryResponse.from(country);   // nothing changes
        }
        if (country == null) {
            country = SupportedCountry.builder().code(upper).name(Locale.of("", upper).getDisplayCountry(Locale.ENGLISH)).build();
        }
        country.setCurrencyCode(currency);
        country.setEnabled(true);
        SupportedCountry saved = countryRepository.save(country);
        Map<String, Object> details = details("code", upper, "name", saved.getName());
        if (wasEnabled) {
            details.put("changes", List.of(details("field", "currencyCode", "from", before, "to", currency)));
            auditLogService.recordByCurrentUser("country.updated", "Country", null, null, details);
        } else {
            details.put("currencyCode", currency);
            auditLogService.recordByCurrentUser("country.enabled", "Country", null, null, details);
        }
        return CountryResponse.from(saved);
    }

    @Override
    @Transactional
    public void disableCountry(String code) {
        accessGuard.requireAdmin();
        String upper = upper(code);
        SupportedCountry country = countryRepository.findById(upper).filter(SupportedCountry::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Country " + upper + " is not enabled"));
        if (countryRepository.usedByOrganization(upper)) {
            throw new ConflictException("An organization is in " + country.getName());
        }
        if (countryRepository.usedByUpcomingEventVenue(upper, Instant.now())) {
            throw new ConflictException("An upcoming event's venue is in " + country.getName());
        }
        country.setEnabled(false);
        countryRepository.save(country);
        auditLogService.recordByCurrentUser("country.disabled", "Country", null, null, details("code", upper, "name", country.getName()));
    }

    // ---- reads and checks for other modules ----

    @Override
    @Transactional(readOnly = true)
    public RegionsResponse regions() {
        return RegionsResponse.from(currencyRepository.findByEnabledTrueOrderByCode(), countryRepository.findByEnabledTrueOrderByName());
    }

    @Override
    public String requireEnabledCountry(String code) {
        return enabledCountry(code).getCode();
    }

    @Override
    public String currencyOfCountry(String code) {
        return enabledCountry(code).getCurrencyCode();
    }

    @Override
    public void requireOrganizationCurrency(UUID organizationId, String currency) {
        String expected = organizationRepository.findById(organizationId).map(Organization::getCurrency).orElse(null);
        if (expected != null && !expected.equals(currency)) {
            throw new BadRequestException("Prices must be in the organization's currency, " + expected);
        }
    }

    private SupportedCountry enabledCountry(String code) {
        String upper = upper(code);
        return countryRepository.findById(upper).filter(SupportedCountry::isEnabled)
                .orElseThrow(() -> new BadRequestException("country " + upper + " is not a supported country"));
    }

    // ---- helpers ----

    private Map<String, BigDecimal> fetchRates() {
        try {
            return exchangeRateClient.usdRates();
        } catch (RuntimeException e) {
            log.warn("Exchange rates could not be fetched: {}", e.toString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The exchange-rate feed is unavailable; try again later");
        }
    }

    private static void setRate(SupportedCurrency currency, BigDecimal rate) {
        currency.setUsdRate(rate);
        currency.setRateUpdatedAt(Instant.now());
        currency.setRateStale(false);
    }

    private static Currency isoCurrency(String code) {
        try {
            Currency currency = Currency.getInstance(upper(code));
            if (currency.getDefaultFractionDigits() < 0) {
                throw new IllegalArgumentException();   // gold, SDR, test codes: not money people pay with
            }
            return currency;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown currency code " + code);
        }
    }

    /** The local symbol (₱, $, €), taken from a locale of a country that uses the currency; the code when there is none. */
    private static String symbolOf(Currency currency) {
        return Arrays.stream(Locale.getAvailableLocales())
                .filter(locale -> !locale.getCountry().isEmpty() && currency.equals(currencyOf(locale)))
                .map(currency::getSymbol)
                .filter(symbol -> !symbol.equals(currency.getCurrencyCode()))
                .min(Comparator.comparingInt(String::length))
                .orElse(currency.getCurrencyCode());
    }

    private static Currency currencyOf(Locale locale) {
        try {
            return Currency.getInstance(locale);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String upper(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, Object> details(Object... pairs) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            details.put((String) pairs[i], pairs[i + 1]);
        }
        return details;
    }
}
