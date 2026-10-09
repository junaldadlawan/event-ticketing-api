package com.junaldadlawan.event_ticketing_api.post.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.post.enums.PostKind;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;

/**
 * Body of {@code PATCH /api/v1/posts/{postId}}. Every field is optional; one that is left out (or null) is not
 * changed. The event of a post cannot be changed. A date is removed with {@code clearPublishAt} /
 * {@code clearExpiresAt} (a null date means "unchanged" like any other field; the two flags default to false). {@code hidden} switches the post
 * off or on for the public. {@code imageUrl}: omitted or null = unchanged, "" = remove the picture, a URL returned by
 * {@code POST /api/v1/uploads} = replace it (anything else is a 400).
 */
public record PostUpdateRequest(
        PostKind kind,

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

        Boolean hidden,

        Boolean clearPublishAt,

        Boolean clearExpiresAt) implements Serializable {
}
