package com.junaldadlawan.event_ticketing_api.tickettype.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType}
 * creation. Deliberate deviation from openapi.yaml's {@code TicketTypeCreate}
 * schema: {@code quantityTotal}/{@code saleStartAt}/{@code saleEndAt} are
 * required here (not optional as the schema's {@code required: [name, kind,
 * price]} literally states) because BR-EVENT-003 is unambiguous that every
 * ticket type must define its own quantity and sale window. {@code
 * maxPerOrder} stays optional — openapi gives it a real server-side default
 * ({@code default: 10}), applied in the service layer when omitted.
 * <p>
 * {@code saleEndAt} must be after {@code saleStartAt}; enforced as an
 * explicit service-level check (no existing cross-field annotation covers
 * this shape).
 */
public record TicketTypeCreateRequest(
        @NotBlank
        @Size(max = 200)
        @NoHtml
        String name,

        @NotNull
        TicketTypeKind kind,

        @NotNull
        @Valid
        MoneyDto price,

        @NotNull
        @Positive
        Integer quantityTotal,

        @NotNull
        Instant saleStartAt,

        @NotNull
        Instant saleEndAt,

        Integer maxPerOrder) implements Serializable {
}
