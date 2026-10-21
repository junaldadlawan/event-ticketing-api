package com.junaldadlawan.event_ticketing_api.tickettype.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.Valid;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType}
 * partial update (owning organization's owner/organizer, or admin). Matches
 * openapi.yaml's {@code TicketTypeUpdate} schema, which lists
 * name/price/quantityTotal/saleStartAt/saleEndAt/maxPerOrder as updatable
 * (notably not {@code kind}, which is create-time-only). {@code null} on any
 * field means "leave unchanged"; a present-but-blank {@code name} is
 * rejected (mirrors {@code VenueUpdateRequest}/{@code EventUpdateRequest}).
 */
public record TicketTypeUpdateRequest(
        @Size(max = 200)
        @NoHtml
        String name,

        @Valid
        MoneyDto price,

        @Positive
        Integer quantityTotal,

        Instant saleStartAt,

        Instant saleEndAt,

        @Positive
        Integer maxPerOrder,

        /** true = pause sales, false = resume; null leaves it unchanged. */
        Boolean salesPaused,

        /** Display colour "#RRGGBB" (either case). Left out (null) = unchanged; JSON null (empty) = clear it. */
        @JsonDeserialize(using = PresentOrNullString.class)
        Optional<@Pattern(regexp = COLOR, message = "must be a #RRGGBB colour") String> color,

        /** BR-TICKET-LIMIT-001 per-buyer limit. Left out (null) = unchanged; JSON null (empty) = unlimited. */
        @JsonDeserialize(using = PresentOrNullInteger.class)
        Optional<@Min(1) Integer> maxPerUser,

        /** BR-TICKET-GATE-001 gate time in minutes. Left out (null) = unchanged; JSON null (empty) = not set. */
        @JsonDeserialize(using = PresentOrNullInteger.class)
        Optional<@Min(0) @Max(1440) Integer> gateOpensMinutesBefore) implements Serializable {

    public static final String COLOR = "^#[0-9A-Fa-f]{6}$";

    /** Jackson's own Optional handling turns a left-out field into empty too; this keeps "left out" as null. */
    abstract static class PresentOrNull<T> extends ValueDeserializer<Optional<T>> {
        private final Class<T> type;

        PresentOrNull(Class<T> type) {
            this.type = type;
        }

        @Override
        public Optional<T> deserialize(JsonParser p, DeserializationContext ctxt) {
            return Optional.of(ctxt.readValue(p, type));
        }

        @Override
        public Optional<T> getNullValue(DeserializationContext ctxt) {
            return Optional.empty();
        }

        @Override
        public Object getAbsentValue(DeserializationContext ctxt) {
            return null;
        }
    }

    static class PresentOrNullString extends PresentOrNull<String> {
        PresentOrNullString() {
            super(String.class);
        }
    }

    static class PresentOrNullInteger extends PresentOrNull<Integer> {
        PresentOrNullInteger() {
            super(Integer.class);
        }
    }

    /** The update without a per-buyer limit or gate time change. */
    public TicketTypeUpdateRequest(String name, MoneyDto price, Integer quantityTotal, Instant saleStartAt,
                                   Instant saleEndAt, Integer maxPerOrder, Boolean salesPaused, Optional<String> color) {
        this(name, price, quantityTotal, saleStartAt, saleEndAt, maxPerOrder, salesPaused, color, null, null);
    }

    /** The update without a colour change. */
    public TicketTypeUpdateRequest(String name, MoneyDto price, Integer quantityTotal, Instant saleStartAt,
                                   Instant saleEndAt, Integer maxPerOrder, Boolean salesPaused) {
        this(name, price, quantityTotal, saleStartAt, saleEndAt, maxPerOrder, salesPaused, null, null, null);
    }

    /** The update without a pause / resume or colour change. */
    public TicketTypeUpdateRequest(String name, MoneyDto price, Integer quantityTotal, Instant saleStartAt,
                                   Instant saleEndAt, Integer maxPerOrder) {
        this(name, price, quantityTotal, saleStartAt, saleEndAt, maxPerOrder, null, null, null, null);
    }
}
