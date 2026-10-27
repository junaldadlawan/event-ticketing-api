package com.junaldadlawan.event_ticketing_api.region.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.io.Serializable;

/** Body of {@code PUT /admin/countries/{code}}: the currency the country's organizations get (must be enabled). */
public record CountryUpsertRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter ISO 4217 currency code")
        String currencyCode) implements Serializable {
}
