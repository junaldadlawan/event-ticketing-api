package com.junaldadlawan.event_ticketing_api.promocode.dto;

import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for {@link PromoCode}, matching openapi.yaml's {@code PromoCode} schema.
 */
public record PromoCodeResponse(
        UUID id,
        UUID eventId,
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        Set<UUID> applicableTicketTypeIds,
        Integer usageLimitTotal,
        Integer usageLimitPerBuyer,
        Instant validFrom,
        Instant validUntil,
        boolean paused,
        int usedCount,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) implements Serializable {

    /**
     * {@code usedCount}: how many (non-cancelled) orders used this code for its event - what the organizer needs to
     * decide what may still be edited or deleted.
     */
    public static PromoCodeResponse from(PromoCode promoCode, int usedCount) {
        return new PromoCodeResponse(
                promoCode.getId(),
                promoCode.getEventId(),
                promoCode.getCode(),
                promoCode.getDiscountType(),
                promoCode.getDiscountValue(),
                promoCode.getApplicableTicketTypeIds(),
                promoCode.getUsageLimitTotal(),
                promoCode.getUsageLimitPerBuyer(),
                promoCode.getValidFrom(),
                promoCode.getValidUntil(),
                promoCode.isPaused(),
                usedCount,
                promoCode.getCreatedBy(),
                promoCode.getCreatedAt(),
                promoCode.getUpdatedBy(),
                promoCode.getUpdatedAt());
    }
}
