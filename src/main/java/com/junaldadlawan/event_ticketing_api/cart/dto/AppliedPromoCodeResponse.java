package com.junaldadlawan.event_ticketing_api.cart.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;

/**
 * Matches openapi.yaml's {@code Cart.applied_promo_code} inline shape
 * ({@code {code, discount_amount, status}}), computed live by {@code
 * CartServiceImpl} rather than persisted. A code that was paused or ran out of its validity window after it was
 * applied still shows (discount 0), so the buyer can see it and remove it - checkout refuses it until then.
 */
public record AppliedPromoCodeResponse(
        String code,
        MoneyDto discountAmount,
        Status status) implements Serializable {

    public enum Status {
        ACTIVE,
        /** the organizer paused the code */
        PAUSED,
        /** outside the code's validity window */
        EXPIRED
    }

    public AppliedPromoCodeResponse(String code, MoneyDto discountAmount) {
        this(code, discountAmount, Status.ACTIVE);
    }
}
