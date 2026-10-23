package com.junaldadlawan.event_ticketing_api.review.dto;

import com.junaldadlawan.event_ticketing_api.review.entity.EventReview;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** The caller's own review, comment included (they wrote it). */
public record EventReviewResponse(
        UUID id,
        UUID eventId,
        int rating,
        String comment,
        Instant createdAt,
        Instant updatedAt) implements Serializable {

    public static EventReviewResponse from(EventReview review) {
        return new EventReviewResponse(review.getId(), review.getEventId(), review.getRating(), review.getComment(),
                review.getCreatedAt(), review.getUpdatedAt());
    }
}
