package com.junaldadlawan.event_ticketing_api.analytics.controller;

import com.junaldadlawan.event_ticketing_api.analytics.dto.ShareLinkOpenResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.ShareLinkResponse;
import com.junaldadlawan.event_ticketing_api.analytics.service.EventShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Public share tracking: the share-button click, the event's short link and the opening of that link. */
@RestController
@RequiredArgsConstructor
public class EventShareController {

    private final EventShareService eventShareService;

    /** The body is optional; an unknown or missing value falls back to the default of that field. */
    public record ClickRequest(String action) {
    }

    public record SourceRequest(String source) {
    }

    /** Always 204, so it never tells whether the event exists. */
    @PostMapping("/api/v1/events/{eventId}/share-clicks")
    public ResponseEntity<Void> click(@PathVariable UUID eventId, @RequestBody(required = false) ClickRequest body) {
        eventShareService.recordClick(eventId, body == null ? null : body.action());
        return ResponseEntity.noContent().build();
    }

    /** Always 204, so it never tells whether the event exists. */
    @PostMapping("/api/v1/events/{eventId}/share-buy-clicks")
    public ResponseEntity<Void> buyClick(@PathVariable UUID eventId, @RequestBody(required = false) SourceRequest body) {
        eventShareService.recordBuyClick(eventId, body == null ? null : body.source());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/events/{eventId}/share-link")
    public ShareLinkResponse shareLink(@PathVariable UUID eventId) {
        return eventShareService.shareLink(eventId);
    }

    @PostMapping("/api/v1/share-links/{code}/open")
    public ShareLinkOpenResponse open(@PathVariable String code, @RequestBody(required = false) SourceRequest body) {
        return eventShareService.open(code, body == null ? null : body.source());
    }
}
