package com.junaldadlawan.event_ticketing_api.waitlist.service;

import com.junaldadlawan.event_ticketing_api.waitlist.dto.WaitlistEntryResponse;

import java.util.List;
import java.util.UUID;

public interface WaitlistService {

    /** {@code POST /events/{eventId}/waitlist} - any authenticated user; 409 if the event/ticket type isn't currently sold out (BR-WAIT-001). */
    WaitlistEntryResponse join(UUID eventId, UUID ticketTypeId);

    /** {@code GET /users/me/waitlist-entries} - the caller's own entries. */
    List<WaitlistEntryResponse> listMyEntries();
}
