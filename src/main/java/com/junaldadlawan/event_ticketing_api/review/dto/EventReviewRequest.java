package com.junaldadlawan.event_ticketing_api.review.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** {@code POST /events/{eventId}/reviews} and {@code PUT .../reviews/me}: the whole review (a PUT replaces it). */
public record EventReviewRequest(
        @NotNull
        @Min(1)
        @Max(5)
        Integer rating,

        /** Optional; only the organizers ever see it. Blank = none. */
        @Size(max = 1000)
        @NoHtml
        String comment) implements Serializable {

    public String commentOrNull() {
        return comment == null || comment.isBlank() ? null : comment.trim();
    }
}
