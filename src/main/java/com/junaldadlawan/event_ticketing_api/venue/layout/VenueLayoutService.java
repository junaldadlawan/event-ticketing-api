package com.junaldadlawan.event_ticketing_api.venue.layout;

import java.util.UUID;

/** Reads and replaces a venue's seat layout. */
public interface VenueLayoutService {

    /** The layout (members of the venue's organization and admins), or an empty plan at version 0 when none was saved. */
    VenueLayoutResponse get(UUID venueId);

    /** Replaces the whole layout and adds one to its version (the organization's owner, or an organizer holding EVENTS). */
    VenueLayoutResponse replace(UUID venueId, VenueLayout layout);
}
