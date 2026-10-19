package com.junaldadlawan.event_ticketing_api.analytics.controller;

import com.junaldadlawan.event_ticketing_api.analytics.service.EventViewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** {@code POST /api/v1/events/{eventId}/views} - public (BR-ANALYTICS-003). Always 204. */
@RestController
@RequiredArgsConstructor
public class EventViewController {

    private final EventViewService eventViewService;

    public record ViewRequest(String visitorId) {
    }

    @PostMapping("/api/v1/events/{eventId}/views")
    public ResponseEntity<Void> view(@PathVariable UUID eventId, @RequestBody(required = false) ViewRequest body, HttpServletRequest request) {
        eventViewService.recordView(eventId, body == null ? null : body.visitorId(), clientKey(request));
        return ResponseEntity.noContent().build();
    }

    /**
     * The client the rate limit counts against: behind the load balancer that is the last X-Forwarded-For entry (the address the balancer
     * itself saw, which the client cannot forge), else the connection's address. Used in memory only, never stored.
     */
    static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
