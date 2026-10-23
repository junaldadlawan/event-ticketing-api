package com.junaldadlawan.event_ticketing_api.review.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.event.service.EventCompletionService;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewListItem;
import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewRequest;
import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewResponse;
import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewSummary;
import com.junaldadlawan.event_ticketing_api.review.entity.EventReview;
import com.junaldadlawan.event_ticketing_api.review.repository.EventReviewRepository;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventReviewServiceImpl implements EventReviewService {

    private final EventReviewRepository eventReviewRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final OrganizationAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    @Override
    public EventReviewResponse create(UUID eventId, EventReviewRequest request) {
        UUID callerId = requireMayReview(eventId);
        if (eventReviewRepository.findByEventIdAndUserIdAndDeletedAtIsNull(eventId, callerId).isPresent()) {
            throw new ConflictException("You have already reviewed this event; edit your review instead");
        }
        EventReview saved;
        try {
            saved = eventReviewRepository.saveAndFlush(EventReview.builder().eventId(eventId).userId(callerId)
                    .rating(request.rating()).comment(request.commentOrNull()).build());
        } catch (DataIntegrityViolationException e) {
            // a second create racing this one won the unique index
            throw new ConflictException("You have already reviewed this event; edit your review instead");
        }
        // the comment is the organizers' to read in the review list, so it stays out of the activity log
        auditLogService.recordByCurrentUser("review.created", "EventReview", saved.getId(), eventId, Map.<String, Object>of("rating", saved.getRating()));
        return EventReviewResponse.from(saved);
    }

    @Override
    @Transactional
    public EventReviewResponse updateMine(UUID eventId, EventReviewRequest request) {
        EventReview review = getMineOrThrow(eventId, requireMayReview(eventId));
        int from = review.getRating();
        review.setRating(request.rating());
        review.setComment(request.commentOrNull());
        EventReview saved = eventReviewRepository.save(review);
        Map<String, Object> details = new LinkedHashMap<>();
        if (from != saved.getRating()) {
            details.put("changes", List.of(Map.of("field", "rating", "from", from, "to", saved.getRating())));
        }
        auditLogService.recordByCurrentUser("review.updated", "EventReview", saved.getId(), eventId, details);
        return EventReviewResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteMine(UUID eventId) {
        EventReview review = getMineOrThrow(eventId, requireMayReview(eventId));
        review.markDeleted();
        eventReviewRepository.save(review);
        auditLogService.recordByCurrentUser("review.deleted", "EventReview", review.getId(), eventId, Map.<String, Object>of("rating", review.getRating()));
    }

    @Override
    public EventReviewResponse getMine(UUID eventId) {
        getEventOrThrow(eventId);
        return EventReviewResponse.from(getMineOrThrow(eventId, accessGuard.currentUserId()));
    }

    @Override
    public EventReviewSummary summary(UUID eventId) {
        getEventOrThrow(eventId);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            counts.put(String.valueOf(star), 0L);
        }
        long count = 0;
        long total = 0;
        for (Object[] row : eventReviewRepository.countByRating(eventId)) {
            int star = ((Number) row[0]).intValue();
            long n = ((Number) row[1]).longValue();
            counts.put(String.valueOf(star), n);
            count += n;
            total += star * n;
        }
        Double average = count == 0 ? null : Math.round(total * 100.0 / count) / 100.0;
        return new EventReviewSummary(average, count, counts);
    }

    @Override
    public Page<EventReviewListItem> list(UUID eventId, Pageable pageable) {
        Event event = getEventOrThrow(eventId);
        if (!accessGuard.isAdmin()
                && !accessGuard.hasPermission(accessGuard.currentUserId(), event.getOrganizationId(), OrganizationPermission.ANALYTICS)) {
            throw new ForbiddenException("Only the organization's owner, an organizer with the Analytics permission, or an admin may read reviews");
        }
        return eventReviewRepository.findListItems(eventId, pageable);
    }

    /** The caller, when they hold a USED ticket for the event (403 otherwise) and it has ended (409 before). */
    private UUID requireMayReview(UUID eventId) {
        Event event = getEventOrThrow(eventId);
        UUID callerId = accessGuard.currentUserId();
        if (!ticketRepository.existsByEventIdAndOwnerIdAndStatus(eventId, callerId, TicketStatus.USED)) {
            throw new ForbiddenException("Only someone who attended the event (a checked-in ticket) may review it");
        }
        if (!EventCompletionService.hasEnded(event)) {
            throw new ConflictException("Reviews open once the event has ended");
        }
        return callerId;
    }

    private EventReview getMineOrThrow(UUID eventId, UUID userId) {
        return eventReviewRepository.findByEventIdAndUserIdAndDeletedAtIsNull(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this event"));
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }
}
