package com.junaldadlawan.event_ticketing_api.venue.layout;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code GET/PUT /api/v1/venues/{venueId}/layout}: the venue's seat layout, saved whole.
 *
 * @deprecated layouts now belong to the organization ({@code /organizations/{orgId}/layouts}, {@code /layouts/{layoutId}}); ticket types no longer
 * copy seats from this one. Kept for one release while the web app moves over.
 */
@Deprecated
@RestController
@RequiredArgsConstructor
public class VenueLayoutController {

    private final VenueLayoutService venueLayoutService;

    @GetMapping("/api/v1/venues/{venueId}/layout")
    public VenueLayoutResponse get(@PathVariable UUID venueId) {
        return venueLayoutService.get(venueId);
    }

    @PutMapping("/api/v1/venues/{venueId}/layout")
    public VenueLayoutResponse replace(@PathVariable UUID venueId, @RequestBody VenueLayout layout) {
        return venueLayoutService.replace(venueId, layout);
    }
}
