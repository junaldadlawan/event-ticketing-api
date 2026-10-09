package com.junaldadlawan.event_ticketing_api.promocode.service;

import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeCreateRequest;
import com.junaldadlawan.event_ticketing_api.promocode.dto.PromoCodeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.PromoCodeStatus;

import java.util.List;
import java.util.UUID;

public interface PromoCodeService {

    PromoCode create(UUID eventId, PromoCodeCreateRequest request);

    /** Every promo code of the event that is not deleted - paused ones included (owning organizer / admin). */
    List<PromoCode> list(UUID eventId);

    /**
     * Partial update (owning organizer / admin): null = unchanged. Once the code has been used, its {@code code}
     * and {@code discountType} can no longer change (409) and a new total limit can't be below the uses (400).
     */
    PromoCode update(UUID promoCodeId, PromoCodeUpdateRequest request);

    /** Pause or resume a promo code (owning organizer / admin). Asking for the state it is already in changes nothing. */
    PromoCode setStatus(UUID promoCodeId, PromoCodeStatus status);

    /** Soft-deletes a promo code that nothing has used yet (owning organizer / admin); a used one can only be paused (409). */
    void delete(UUID promoCodeId);

    /** How many (non-cancelled) orders used this promo code for its event. */
    int usedCount(PromoCode promoCode);
}
