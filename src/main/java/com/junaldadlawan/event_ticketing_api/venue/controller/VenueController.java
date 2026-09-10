package com.junaldadlawan.event_ticketing_api.venue.controller;

import com.junaldadlawan.event_ticketing_api.venue.dto.VenueResponse;
import com.junaldadlawan.event_ticketing_api.venue.dto.VenueUpdateRequest;
import com.junaldadlawan.event_ticketing_api.venue.service.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @GetMapping("/{venueId}")
    public VenueResponse get(@PathVariable UUID venueId) {
        return VenueResponse.from(venueService.get(venueId));
    }

    @PatchMapping("/{venueId}")
    public VenueResponse update(@PathVariable UUID venueId, @Valid @RequestBody VenueUpdateRequest request) {
        return VenueResponse.from(venueService.update(venueId, request));
    }
}
