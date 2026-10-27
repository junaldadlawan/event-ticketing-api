package com.junaldadlawan.event_ticketing_api.region.controller;

import com.junaldadlawan.event_ticketing_api.region.dto.CountryResponse;
import com.junaldadlawan.event_ticketing_api.region.dto.CountryUpsertRequest;
import com.junaldadlawan.event_ticketing_api.region.dto.CurrencyEnableRequest;
import com.junaldadlawan.event_ticketing_api.region.dto.CurrencyResponse;
import com.junaldadlawan.event_ticketing_api.region.dto.RegionsResponse;
import com.junaldadlawan.event_ticketing_api.region.service.RegionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** Platform currencies and countries. /admin/** is admin-only in SecurityConfig (and checked again in the service); /platform/regions is public. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @GetMapping("/platform/regions")
    public ResponseEntity<RegionsResponse> regions() {
        // rates change once a day; a few minutes' cache keeps an admin's change visible soon
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()).body(regionService.regions());
    }

    @PostMapping("/admin/currencies")
    @ResponseStatus(HttpStatus.CREATED)
    public CurrencyResponse enableCurrency(@Valid @RequestBody CurrencyEnableRequest request) {
        return regionService.enableCurrency(request.code());
    }

    @DeleteMapping("/admin/currencies/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableCurrency(@PathVariable String code) {
        regionService.disableCurrency(code);
    }

    @PostMapping("/admin/currencies/refresh")
    public List<CurrencyResponse> refreshRates() {
        return regionService.refreshRates();
    }

    @PutMapping("/admin/countries/{code}")
    public CountryResponse putCountry(@PathVariable String code, @Valid @RequestBody CountryUpsertRequest request) {
        return regionService.putCountry(code, request.currencyCode());
    }

    @DeleteMapping("/admin/countries/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableCountry(@PathVariable String code) {
        regionService.disableCountry(code);
    }
}
