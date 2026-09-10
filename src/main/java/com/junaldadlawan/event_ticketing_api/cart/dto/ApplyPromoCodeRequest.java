package com.junaldadlawan.event_ticketing_api.cart.dto;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/** Matches openapi.yaml's {@code applyPromoCode} request body. */
public record ApplyPromoCodeRequest(
        @NotBlank
        String code) implements Serializable {
}
