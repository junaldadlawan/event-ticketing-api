package com.junaldadlawan.event_ticketing_api.cart.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;

/**
 * Matches openapi.yaml's {@code Cart.applied_promo_code} inline shape
 * ({@code {code, discount_amount}}), computed live by {@code
 * CartServiceImpl} rather than persisted.
 */
public record AppliedPromoCodeResponse(
        String code,
        MoneyDto discountAmount) implements Serializable {
}
