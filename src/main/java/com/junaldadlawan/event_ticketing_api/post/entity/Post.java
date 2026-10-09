package com.junaldadlawan.event_ticketing_api.post.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import com.junaldadlawan.event_ticketing_api.post.enums.PostKind;
import com.junaldadlawan.event_ticketing_api.post.enums.PostStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An announcement or sale written by an admin. {@code eventId} null = site-wide (Home page); otherwise the
 * post belongs to that event's landing page. "Managed resource" tier: the full {@link Auditable} set, with
 * {@code markDeleted()} as the soft delete.
 */
@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private PostKind kind;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    /** Never null; an empty string when the admin wrote no details. */
    @Builder.Default
    @Column(name = "body", nullable = false, length = 2000)
    private String body = "";

    /** Not public before this moment; null = public as soon as it is created. */
    @Column(name = "publish_at")
    private Instant publishAt;

    /** Not public from this moment on; null = never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** URL of the post picture - one of our own uploads - or null for a text-only post. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Admin switch: a hidden post is never public, whatever its dates say. */
    @Builder.Default
    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;

    public PostStatus statusAt(Instant now) {
        if (hidden) {
            return PostStatus.HIDDEN;
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return PostStatus.EXPIRED;
        }
        if (publishAt != null && publishAt.isAfter(now)) {
            return PostStatus.SCHEDULED;
        }
        return PostStatus.LIVE;
    }
}
