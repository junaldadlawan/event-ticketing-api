package com.junaldadlawan.event_ticketing_api.review.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** One row of the organizers' review list ({@code GET /events/{eventId}/reviews}). */
public record EventReviewListItem(
        UUID id,
        int rating,
        String comment,
        String reviewerName,
        Instant createdAt) implements Serializable {
}
