package com.junaldadlawan.event_ticketing_api.order.gateway;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;

/** PayMongo refused to create the intent because of setup_future_usage (card vaulting is not enabled on this account): the payment itself can still be made. */
public class CardSavingUnavailableException extends BadRequestException {
    public CardSavingUnavailableException(String message) {
        super(message);
    }
}
