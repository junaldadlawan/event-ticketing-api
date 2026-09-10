package com.junaldadlawan.event_ticketing_api.tickettype.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;

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
        Integer maxPerOrder) implements Serializable {
}
