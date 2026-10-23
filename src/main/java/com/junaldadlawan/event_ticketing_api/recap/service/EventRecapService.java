package com.junaldadlawan.event_ticketing_api.recap.service;

import com.junaldadlawan.event_ticketing_api.recap.dto.RecapMessageRequest;
import com.junaldadlawan.event_ticketing_api.recap.dto.RecapMessageResponse;

import java.util.List;
import java.util.UUID;

/** BR-EVENT-RECAP-001: the organizer's feed of messages and pictures for an event that has ended. */
public interface EventRecapService {

    /** Owner, or an organizer with EVENTS; only once the event has ended. */
    RecapMessageResponse post(UUID eventId, RecapMessageRequest request);

    /** The same people as {@link #post}. */
    RecapMessageResponse update(UUID eventId, UUID messageId, RecapMessageRequest request);

    /** The same people as {@link #post}, or an admin. */
    void delete(UUID eventId, UUID messageId);

    /** Public for a published event that has ended (its managers and admins may always read it); oldest first. */
    List<RecapMessageResponse> feed(UUID eventId);
}
