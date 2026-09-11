package com.junaldadlawan.event_ticketing_api.waitlist.dto;

import java.io.Serializable;
import java.util.UUID;

/** Matches openapi.yaml's {@code POST /events/{eventId}/waitlist} request body. Omit {@code ticketTypeId} to wait for the event generally. */
public record WaitlistJoinRequest(
        UUID ticketTypeId) implements Serializable {
}
