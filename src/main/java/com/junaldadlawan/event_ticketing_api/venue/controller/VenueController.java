package com.junaldadlawan.event_ticketing_api.venue.controller;

import com.junaldadlawan.event_ticketing_api.venue.dto.VenueCreateRequest;
import com.junaldadlawan.event_ticketing_api.venue.dto.VenueResponse;
import com.junaldadlawan.event_ticketing_api.venue.dto.VenueUpdateRequest;
import com.junaldadlawan.event_ticketing_api.venue.service.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * All venue endpoints: create/list are addressed through the owning
 * organization, get/update by the venue's own id.
 */
@RestController
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @PostMapping("/api/v1/organizations/{orgId}/venues")
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse create(@PathVariable UUID orgId, @Valid @RequestBody VenueCreateRequest request) {
        return VenueResponse.from(venueService.create(orgId, request));
    }

    @GetMapping("/api/v1/organizations/{orgId}/venues")
    public List<VenueResponse> list(@PathVariable UUID orgId) {
        return venueService.list(orgId).stream().map(VenueResponse::from).toList();
    }

    @GetMapping("/api/v1/venues/{venueId}")
    public VenueResponse get(@PathVariable UUID venueId) {
        return VenueResponse.from(venueService.get(venueId));
    }

    @PatchMapping("/api/v1/venues/{venueId}")
    public VenueResponse update(@PathVariable UUID venueId, @Valid @RequestBody VenueUpdateRequest request) {
        return VenueResponse.from(venueService.update(venueId, request));
    }
}
