package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.dto.ShareLinkOpenResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.ShareLinkResponse;

import java.util.UUID;

/** Share tracking: how often an event's share button is clicked and its short link is opened. No visitor data is kept. */
public interface EventShareService {

    /**
     * Counts one click in the Share window: {@code action} is SHARE_BUTTON, COPY_LINK or QR_GENERATE (anything else, or none, is SHARE_BUTTON).
     * Silently does nothing for an event that is not publicly visible.
     */
    void recordClick(UUID eventId, String action);

    /** Counts one buy click from a shared visit: {@code source} is LINK or QR; anything else counts nothing. Same visibility rule as clicks. */
    void recordBuyClick(UUID eventId, String source);

    /** The event's share code (created the first time it is asked for); 404 for an event that is not publicly visible. */
    ShareLinkResponse shareLink(UUID eventId);

    /**
     * Counts one open of the link behind {@code code} and returns its event: {@code source} is LINK or QR (anything else, or none, is LINK).
     * 404 (nothing counted) for an unknown code.
     */
    ShareLinkOpenResponse open(String code, String source);
}
