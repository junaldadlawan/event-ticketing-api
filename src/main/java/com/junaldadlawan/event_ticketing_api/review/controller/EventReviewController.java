package com.junaldadlawan.event_ticketing_api.review.controller;

import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewListItem;
import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewRequest;
import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewResponse;
import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewSummary;
import com.junaldadlawan.event_ticketing_api.review.service.EventReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/reviews")
@RequiredArgsConstructor
public class EventReviewController {

    private final EventReviewService eventReviewService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventReviewResponse create(@PathVariable UUID eventId, @Valid @RequestBody EventReviewRequest request) {
        return eventReviewService.create(eventId, request);
    }

    @GetMapping("/me")
    public EventReviewResponse getMine(@PathVariable UUID eventId) {
        return eventReviewService.getMine(eventId);
    }

    @PutMapping("/me")
    public EventReviewResponse updateMine(@PathVariable UUID eventId, @Valid @RequestBody EventReviewRequest request) {
        return eventReviewService.updateMine(eventId, request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMine(@PathVariable UUID eventId) {
        eventReviewService.deleteMine(eventId);
    }

    @GetMapping("/summary")
    public EventReviewSummary summary(@PathVariable UUID eventId) {
        return eventReviewService.summary(eventId);
    }

    @GetMapping
    public PageResponse<EventReviewListItem> list(@PathVariable UUID eventId,
                                                  @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(eventReviewService.list(eventId, pageable));
    }
}
