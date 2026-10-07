package com.junaldadlawan.event_ticketing_api.post.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.post.enums.PostKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/posts}. {@code eventId} null or missing = a site-wide post (shown on Home); otherwise
 * the post belongs to that event. {@code title} is required (1-120, trimmed); {@code body} is optional (up to 2000).
 * {@code publishAt} schedules the post (null = public right away), {@code expiresAt} takes it down again (null =
 * never; it must be in the future and after {@code publishAt}), {@code hidden} keeps it off the public list.
 * {@code imageUrl} is an optional picture: the URL returned by {@code POST /api/v1/uploads} (anything else is a 400).
 */
public record PostCreateRequest(
        UUID eventId,

        @NotNull
        PostKind kind,

        @NotBlank
        @Size(max = 120)
        @NoHtml
        String title,

        @Size(max = 2000)
        @NoHtml
        String body,

        @Size(max = 500)
        String imageUrl,

        Instant publishAt,

        Instant expiresAt,

        Boolean hidden) implements Serializable {

    /** The common case: no schedule, no expiry, public. */
    public PostCreateRequest(UUID eventId, PostKind kind, String title, String body) {
        this(eventId, kind, title, body, null, null, null, null);
    }
}
