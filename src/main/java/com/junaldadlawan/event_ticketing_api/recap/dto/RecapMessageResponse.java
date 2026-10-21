package com.junaldadlawan.event_ticketing_api.recap.dto;

import com.junaldadlawan.event_ticketing_api.recap.entity.EventRecapMessage;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** One recap message as the feed shows it: who wrote it, for which organization. */
public record RecapMessageResponse(
        UUID id,
        String body,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt,
        String authorName,
        String organizationName) implements Serializable {

    public static RecapMessageResponse from(EventRecapMessage message, String authorName, String organizationName) {
        return new RecapMessageResponse(message.getId(), message.getBody(), message.getImageUrl(), message.getCreatedAt(),
                message.getUpdatedAt(), authorName, organizationName);
    }
}
