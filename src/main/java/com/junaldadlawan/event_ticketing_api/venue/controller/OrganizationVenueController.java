package com.junaldadlawan.event_ticketing_api.venue.controller;

import com.junaldadlawan.event_ticketing_api.venue.dto.VenueCreateRequest;
import com.junaldadlawan.event_ticketing_api.venue.dto.VenueResponse;
import com.junaldadlawan.event_ticketing_api.venue.service.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/venues")
@RequiredArgsConstructor
public class OrganizationVenueController {

    private final VenueService venueService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse create(@PathVariable UUID orgId, @Valid @RequestBody VenueCreateRequest request) {
        return VenueResponse.from(venueService.create(orgId, request));
    }

    @GetMapping
    public List<VenueResponse> list(@PathVariable UUID orgId) {
        return venueService.list(orgId).stream().map(VenueResponse::from).toList();
    }
}
