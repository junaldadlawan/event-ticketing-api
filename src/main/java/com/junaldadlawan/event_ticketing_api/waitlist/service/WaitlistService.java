package com.junaldadlawan.event_ticketing_api.waitlist.service;

import com.junaldadlawan.event_ticketing_api.waitlist.dto.WaitlistEntryResponse;

import java.util.List;
import java.util.UUID;

public interface WaitlistService {

    /** {@code POST /events/{eventId}/waitlist} - any authenticated user; 409 if the event/ticket type isn't currently sold out (BR-WAIT-001). */
    WaitlistEntryResponse join(UUID eventId, UUID ticketTypeId);

    /** {@code GET /users/me/waitlist-entries} - the caller's own entries. */
    List<WaitlistEntryResponse> listMyEntries();

    /**
     * BR-WAIT-002/003 (Phase 11): a unit of inventory for {@code ticketTypeId}
     * (a GA ticket type - see {@code RefundServiceImpl}'s call site for why
     * reserved-seating tickets don't trigger this) just became available
     * again - offer it to the earliest-joined, not-yet-notified waitlisted
     * user for that specific ticket type, and (since any ticket type
     * un-selling-out also ends the event's overall "generally sold out"
     * state) the earliest-joined not-yet-notified event-general waiter too.
     * A no-op scope with nobody waiting does nothing. Never throws - called
     * from the middle of a refund's transaction, and a waitlist-notify
     * failure must not undo that refund.
     */
    void notifyNextInLineIfAvailable(UUID eventId, UUID ticketTypeId);
}
