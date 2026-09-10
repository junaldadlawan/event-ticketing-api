package com.junaldadlawan.event_ticketing_api.promocode.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode}
 * creation. Matches openapi.yaml's {@code PromoCodeCreate} schema exactly:
 * {@code code}/{@code discountType}/{@code discountValue}/{@code validFrom}/
 * {@code validUntil} required, the rest optional.
 * <p>
 * {@code validUntil} must be after {@code validFrom}; enforced as an
 * explicit service-level check, same pattern as {@code TicketType}'s
 * sale-window check.
 */
public record PromoCodeCreateRequest(
        @NotBlank
        @Size(max = 50)
        @NoHtml
        String code,

        @NotNull
        DiscountType discountType,

        @NotNull
        @PositiveOrZero
        BigDecimal discountValue,

        Set<UUID> applicableTicketTypeIds,

        Integer usageLimitTotal,

        Integer usageLimitPerBuyer,

        @NotNull
        Instant validFrom,

        @NotNull
        Instant validUntil) implements Serializable {
}
