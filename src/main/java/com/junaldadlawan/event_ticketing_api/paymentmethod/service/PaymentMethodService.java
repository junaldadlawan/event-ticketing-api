package com.junaldadlawan.event_ticketing_api.paymentmethod.service;

import com.junaldadlawan.event_ticketing_api.paymentmethod.dto.PaymentMethodRequest;
import com.junaldadlawan.event_ticketing_api.paymentmethod.dto.PaymentMethodResponse;
import com.junaldadlawan.event_ticketing_api.paymentmethod.dto.SetupIntentResponse;

import java.util.List;
import java.util.UUID;

/** The signed-in user's own saved cards. Every method works on the caller only; another user's card id is a 404. */
public interface PaymentMethodService {

    /** The caller's cards, primary first. */
    List<PaymentMethodResponse> list();

    /** A Stripe SetupIntent for the caller's customer (a dummy secret with the mock gateway). */
    SetupIntentResponse createSetupIntent();

    /** Saves a card. The first card is primary; asking for primary unsets the previous one. */
    PaymentMethodResponse add(PaymentMethodRequest request);

    /** Makes the card primary, unsetting the old primary. */
    PaymentMethodResponse setPrimary(UUID id);

    /** Soft-deletes the card and detaches it from Stripe. The primary card cannot go while other cards exist (409). */
    void remove(UUID id);

    /**
     * What checkout needs to charge a saved card of the caller. With PayMongo a card saved before its payment method id was kept cannot be
     * charged: 409 "Re-enter this card to use it again".
     */
    SavedCardCharge resolveForCheckout(UUID id);

    /**
     * After a payment made with "save this card" (PayMongo): reads the cards vaulted on the user's customer and saves those not saved yet.
     * A saved card with the same last 4 and expiry but no payment method id (an old card) gets the id instead of a duplicate. When PayMongo
     * lists no new card, a card the user removed that is still vaulted is restored. Every change is audited and every skipped card logged.
     * Returns how many cards were added, upgraded or restored.
     */
    int saveVaultedCards(UUID userId, String customerId);

    /** The token to charge and, with Stripe, the caller's customer id (null with the mock gateway). */
    record SavedCardCharge(String token, String customerId) {
    }
}
