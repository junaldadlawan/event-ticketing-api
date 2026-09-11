package com.junaldadlawan.event_ticketing_api.checkin.controller;

import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInConfigResponse;
import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInConfigUpdateRequest;
import com.junaldadlawan.event_ticketing_api.checkin.service.CheckInConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * GET has its own {@code SecurityConfig} matcher (any authenticated caller,
 * user or device - not public, unlike resale-policy's GET); PATCH is
 * already covered by the existing {@code PATCH /api/v1/events/**}
 * authenticated matcher.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/check-in-config")
@RequiredArgsConstructor
public class EventCheckInConfigController {

    private final CheckInConfigService checkInConfigService;

    @GetMapping
    public CheckInConfigResponse get(@PathVariable UUID eventId) {
        return checkInConfigService.get(eventId);
    }

    @PatchMapping
    public CheckInConfigResponse update(@PathVariable UUID eventId, @RequestBody CheckInConfigUpdateRequest request) {
        return checkInConfigService.update(eventId, request);
    }
}
