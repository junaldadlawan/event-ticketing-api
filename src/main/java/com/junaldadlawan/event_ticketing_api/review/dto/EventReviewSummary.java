package com.junaldadlawan.event_ticketing_api.review.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * The public {@code GET /events/{eventId}/reviews/summary}: no comments, no reviewers. {@code averageRating} is
 * rounded to 2 decimals and null when there are no reviews; {@code counts} always has the keys "1" to "5".
 */
public record EventReviewSummary(
        Double averageRating,
        long count,
        Map<String, Long> counts) implements Serializable {
}
