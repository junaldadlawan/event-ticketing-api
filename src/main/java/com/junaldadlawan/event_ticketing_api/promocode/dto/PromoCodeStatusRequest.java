package com.junaldadlawan.event_ticketing_api.promocode.dto;

import com.junaldadlawan.event_ticketing_api.promocode.enums.PromoCodeStatus;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * Body of {@code PUT /api/v1/promo-codes/{id}/status}: the state the organizer wants the promo code to be in.
 * Asking for the state it is already in is fine and changes nothing.
 */
public record PromoCodeStatusRequest(
        @NotNull
        PromoCodeStatus status) implements Serializable {
}
