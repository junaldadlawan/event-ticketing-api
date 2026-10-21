package com.junaldadlawan.event_ticketing_api.tickettype.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType}
 * creation. Deliberate deviation from openapi.yaml's {@code TicketTypeCreate}
 * schema: {@code quantityTotal}/{@code saleEndAt} are required here (not
 * optional as the schema's {@code required: [name, kind, price]} literally
 * states) because BR-EVENT-003 is unambiguous that every ticket type must
 * define its own quantity and sale window. {@code saleStartAt} may be omitted
 * to mean "start now" (taken from the server's clock). {@code
 * maxPerOrder} stays optional — openapi gives it a real server-side default
 * ({@code default: 10}), applied in the service layer when omitted.
 * <p>
 * {@code saleEndAt} must be at least 1 hour after {@code saleStartAt} and at
 * least 1 hour before the event starts; enforced as explicit service-level checks (they
 * need the event).
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

        Instant saleStartAt,

        @NotNull
        Instant saleEndAt,

        Integer maxPerOrder,

        /** Display colour "#RRGGBB" (either case); null = none. */
        @Pattern(regexp = TicketTypeUpdateRequest.COLOR, message = "must be a #RRGGBB colour")
        String color,

        /** BR-TICKET-LIMIT-001: most tickets of this type one buyer may buy in total; null = unlimited. */
        @Min(1)
        Integer maxPerUser,

        /** BR-TICKET-GATE-001: gates open this many minutes before the event starts; null = not set. */
        @Min(0)
        @Max(1440)
        Integer gateOpensMinutesBefore) implements Serializable {

    /** A create without a per-buyer limit or gate time. */
    public TicketTypeCreateRequest(String name, TicketTypeKind kind, MoneyDto price, Integer quantityTotal,
                                   Instant saleStartAt, Instant saleEndAt, Integer maxPerOrder, String color) {
        this(name, kind, price, quantityTotal, saleStartAt, saleEndAt, maxPerOrder, color, null, null);
    }

    /** A create without a colour, per-buyer limit or gate time. */
    public TicketTypeCreateRequest(String name, TicketTypeKind kind, MoneyDto price, Integer quantityTotal,
                                   Instant saleStartAt, Instant saleEndAt, Integer maxPerOrder) {
        this(name, kind, price, quantityTotal, saleStartAt, saleEndAt, maxPerOrder, null, null, null);
    }
}
