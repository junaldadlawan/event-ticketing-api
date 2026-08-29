package com.junaldadlawan.event_ticketing_api.event.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.common.validation.ValidEndTime;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.event.entity.Event}
 */
@ValidEndTime
public record EventRequest(
        @NotBlank
        @Size(max = 200)
        @NoHtml
        String title,

        @Size(max = 10_000)
        @NoHtml
        @NotNull
        String description,

        @NotBlank
        @Size(max = 100)
        @NoHtml String category,
        int venue,

        @NotNull
        @Future
        Instant startAt,

        @NotNull
        Instant endAt,

        @NotBlank
        String timezone,

        byte[] image,

        @NoHtml
        String ticketPrefix)
        implements Serializable {
}