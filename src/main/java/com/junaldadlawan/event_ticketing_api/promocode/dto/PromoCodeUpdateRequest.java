package com.junaldadlawan.event_ticketing_api.promocode.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Body of {@code PATCH /api/v1/promo-codes/{id}}: every member optional, {@code null} (or missing) = unchanged.
 * <ul>
 *   <li>{@code usageLimitTotal} / {@code usageLimitPerBuyer}: send 0 to remove the limit ("no limit");</li>
 *   <li>{@code applicableTicketTypeIds}: send {@code []} to apply to every ticket type of the event;</li>
 *   <li>cross-field rules (validUntil after validFrom, a percentage at most 100, the ticket types belonging
 *       to the same event, no change of code / discount type once the code has been used) are checked in
 *       {@code PromoCodeServiceImpl} against the merged values.</li>
 * </ul>
 */
public record PromoCodeUpdateRequest(
        @Size(max = 50)
        @NoHtml
        String code,

        DiscountType discountType,

        @PositiveOrZero
        BigDecimal discountValue,

        Set<UUID> applicableTicketTypeIds,

        @PositiveOrZero
        Integer usageLimitTotal,

        @PositiveOrZero
        Integer usageLimitPerBuyer,

        Instant validFrom,

        Instant validUntil) implements Serializable {
}
