package com.junaldadlawan.event_ticketing_api.post.dto;

import com.junaldadlawan.event_ticketing_api.post.entity.Post;
import com.junaldadlawan.event_ticketing_api.post.enums.PostKind;
import com.junaldadlawan.event_ticketing_api.post.enums.PostStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@link Post}. {@code eventId} / {@code eventTitle} are null for a site-wide post; the title is included
 * so the Home page can print "Event name: post title" without a call per event. {@code publishAt} / {@code expiresAt}
 * are null when the post has no schedule / never expires; {@code imageUrl} is the optional picture (null when there is none); {@code status} is where the post stands right now
 * (HIDDEN, EXPIRED, SCHEDULED or LIVE) - the public list only ever holds LIVE ones.
 */
public record PostResponse(
        UUID id,
        UUID eventId,
        String eventTitle,
        PostKind kind,
        String title,
        String body,
        String imageUrl,
        Instant publishAt,
        Instant expiresAt,
        boolean hidden,
        PostStatus status,
        Instant createdAt) implements Serializable {

    public static PostResponse from(Post post, String eventTitle) {
        return from(post, eventTitle, Instant.now());
    }

    public static PostResponse from(Post post, String eventTitle, Instant now) {
        return new PostResponse(post.getId(), post.getEventId(), eventTitle, post.getKind(), post.getTitle(),
                post.getBody(), post.getImageUrl(), post.getPublishAt(), post.getExpiresAt(), post.isHidden(), post.statusAt(now),
                post.getCreatedAt());
    }
}
