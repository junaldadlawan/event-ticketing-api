package com.junaldadlawan.event_ticketing_api.event.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.event.entity.Event}
 */
public record EventUpdateRequest(
        @Size(max = 200) @NoHtml String title,
        @Size(max = 10_000) @NoHtml String description,
        @Size(max = 100) @NoHtml String category,
        int venue,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        String timezone,
        byte[] image,
        @NoHtml String ticketPrefix
){
}