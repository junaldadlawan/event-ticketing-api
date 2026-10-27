package com.junaldadlawan.event_ticketing_api.analytics.dto;

/** {@code GET /events/{eventId}/share-link}: the event's short code and the path a shared link uses. */
public record ShareLinkResponse(String code, String path) {

    public static ShareLinkResponse of(String code) {
        return new ShareLinkResponse(code, "/s/" + code);
    }
}
