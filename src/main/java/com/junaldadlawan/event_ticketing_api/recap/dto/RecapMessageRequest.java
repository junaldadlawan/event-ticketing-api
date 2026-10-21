package com.junaldadlawan.event_ticketing_api.recap.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * {@code POST /events/{eventId}/recap} and {@code PATCH .../recap/{messageId}}. A message needs a body or a
 * picture. On PATCH, a null field is left unchanged; {@code body} "" empties the text and {@code imageUrl} ""
 * removes the picture. {@code imageUrl} must be a URL returned by POST /uploads.
 */
public record RecapMessageRequest(
        @Size(max = 2000)
        @NoHtml
        String body,

        @Size(max = 500)
        String imageUrl) implements Serializable {
}
