package com.junaldadlawan.event_ticketing_api.waitlist.controller;

import com.junaldadlawan.event_ticketing_api.waitlist.dto.WaitlistEntryResponse;
import com.junaldadlawan.event_ticketing_api.waitlist.dto.WaitlistJoinRequest;
import com.junaldadlawan.event_ticketing_api.waitlist.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * No class-level {@code @RequestMapping}: the two endpoints here span two
 * different top-level path prefixes ({@code /events/{eventId}/waitlist},
 * {@code /users/me/waitlist-entries}) rather than sharing one, same idiom
 * as {@code OrderController} (Phase 6a).
 */
@RestController
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping("/api/v1/events/{eventId}/waitlist")
    @ResponseStatus(HttpStatus.CREATED)
    public WaitlistEntryResponse join(@PathVariable UUID eventId, @Valid @RequestBody(required = false) WaitlistJoinRequest request) {
        UUID ticketTypeId = request != null ? request.ticketTypeId() : null;
        return waitlistService.join(eventId, ticketTypeId);
    }

    @GetMapping("/api/v1/users/me/waitlist-entries")
    public List<WaitlistEntryResponse> listMyEntries() {
        return waitlistService.listMyEntries();
    }
}
