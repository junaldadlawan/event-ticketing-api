package com.junaldadlawan.event_ticketing_api.review.service;

import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewListItem;
import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewRequest;
import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewResponse;
import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** BR-REVIEW-001: reviews of an event, by attendees (a USED ticket), once it has ended. */
public interface EventReviewService {

    EventReviewResponse create(UUID eventId, EventReviewRequest request);

    EventReviewResponse updateMine(UUID eventId, EventReviewRequest request);

    void deleteMine(UUID eventId);

    /** The caller's own review; 404 when they have none. */
    EventReviewResponse getMine(UUID eventId);

    /** Public: average, total and per-star counts - never comments. */
    EventReviewSummary summary(UUID eventId);

    /** Owner, organizers with ANALYTICS, or admins: every review with its comment and reviewer. */
    Page<EventReviewListItem> list(UUID eventId, Pageable pageable);
}
