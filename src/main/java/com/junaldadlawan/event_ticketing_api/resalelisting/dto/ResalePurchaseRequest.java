package com.junaldadlawan.event_ticketing_api.resalelisting.dto;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/** Matches openapi.yaml's {@code POST /resale-listings/{listingId}/purchase} request body. */
public record ResalePurchaseRequest(
        @NotBlank
        String paymentMethodToken) implements Serializable {
}
