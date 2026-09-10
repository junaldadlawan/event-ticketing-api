package com.junaldadlawan.event_ticketing_api.tickettype.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.io.Serializable;

/**
 * Request-side wrapper for {@code Money} (openapi.yaml). The
 * {@code common/entity/Money} embeddable is a plain JPA value object (no
 * validation, mutable, needs a no-args constructor) rather than a clean
 * validated record, so request DTOs accept this instead and the service
 * layer maps it onto {@code Money}.
 */
public record MoneyDto(
        @PositiveOrZero
        long amount,

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter uppercase ISO 4217 currency code")
        String currency) implements Serializable {
}
