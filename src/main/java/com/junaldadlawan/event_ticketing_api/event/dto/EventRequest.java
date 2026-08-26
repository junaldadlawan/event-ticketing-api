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
public record EventRequest(
        @NotBlank @Size(max = 200) @NoHtml String title,
        @Size(max = 10_000) @NoHtml String description,
        @NotBlank @Size(max = 100) @NoHtml String category,
        @NotBlank @Size(max = 200) @NoHtml int venue,
        @NotNull @Future Instant startAt,
        @NotNull @NoHtml @ValidEndTime Instant endAt,
        @NotBlank @NoHtml String timezone,
        @NoHtml Byte image,
        @NoHtml String ticketPrefix) implements Serializable {
}