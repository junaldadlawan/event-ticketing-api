package com.junaldadlawan.event_ticketing_api.recap.controller;

import com.junaldadlawan.event_ticketing_api.recap.dto.RecapMessageRequest;
import com.junaldadlawan.event_ticketing_api.recap.dto.RecapMessageResponse;
import com.junaldadlawan.event_ticketing_api.recap.service.EventRecapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/recap")
@RequiredArgsConstructor
public class EventRecapController {

    private final EventRecapService eventRecapService;

    @GetMapping
    public List<RecapMessageResponse> feed(@PathVariable UUID eventId) {
        return eventRecapService.feed(eventId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecapMessageResponse post(@PathVariable UUID eventId, @Valid @RequestBody RecapMessageRequest request) {
        return eventRecapService.post(eventId, request);
    }

    @PatchMapping("/{messageId}")
    public RecapMessageResponse update(@PathVariable UUID eventId, @PathVariable UUID messageId,
                                       @Valid @RequestBody RecapMessageRequest request) {
        return eventRecapService.update(eventId, messageId, request);
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID eventId, @PathVariable UUID messageId) {
        eventRecapService.delete(eventId, messageId);
    }
}
