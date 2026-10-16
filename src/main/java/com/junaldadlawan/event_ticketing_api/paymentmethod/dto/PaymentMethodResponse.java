package com.junaldadlawan.event_ticketing_api.paymentmethod.dto;

import com.junaldadlawan.event_ticketing_api.paymentmethod.entity.PaymentMethod;

import java.io.Serializable;
import java.util.UUID;

/** A saved card as shown. The provider's payment-method id and any token are never part of it. */
public record PaymentMethodResponse(
        UUID id,
        String holderName,
        String brand,
        String last4,
        int expMonth,
        int expYear,
        boolean primary,
        boolean canChargeAgain) implements Serializable {

    /** {@code canChargeAgain} false = the card cannot be charged without typing it again (saved before the provider's payment method was kept). */
    public static PaymentMethodResponse from(PaymentMethod card, boolean canChargeAgain) {
        return new PaymentMethodResponse(card.getId(), card.getHolderName(), card.getBrand(), card.getLast4(),
                card.getExpMonth(), card.getExpYear(), card.isPrimary(), canChargeAgain);
    }
}
