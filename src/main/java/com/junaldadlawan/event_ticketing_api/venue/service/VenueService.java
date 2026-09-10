package com.junaldadlawan.event_ticketing_api.venue.service;

import com.junaldadlawan.event_ticketing_api.venue.dto.VenueCreateRequest;
import com.junaldadlawan.event_ticketing_api.venue.dto.VenueUpdateRequest;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;

import java.util.List;
import java.util.UUID;

public interface VenueService {

    Venue create(UUID orgId, VenueCreateRequest request);

    List<Venue> list(UUID orgId);

    Venue get(UUID venueId);

    Venue update(UUID venueId, VenueUpdateRequest request);
}
