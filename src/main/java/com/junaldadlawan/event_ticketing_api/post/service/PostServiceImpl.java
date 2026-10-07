package com.junaldadlawan.event_ticketing_api.post.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.post.dto.PostCreateRequest;
import com.junaldadlawan.event_ticketing_api.post.dto.PostResponse;
import com.junaldadlawan.event_ticketing_api.post.dto.PostUpdateRequest;
import com.junaldadlawan.event_ticketing_api.post.entity.Post;
import com.junaldadlawan.event_ticketing_api.post.repository.PostRepository;
import com.junaldadlawan.event_ticketing_api.upload.service.UploadedFileUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;

    /** An event's posts are public while the event is: not a draft, not cancelled, not suspended. */
    private static final Set<EventStatus> VISIBLE_EVENT_STATUSES =
            EnumSet.of(EventStatus.PUBLISHED, EventStatus.ON_SALE, EventStatus.SOLD_OUT, EventStatus.COMPLETED);

    private final PostRepository postRepository;
    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;
    private final UploadedFileUrls uploadedFileUrls;

    @Override
    public PostResponse create(PostCreateRequest request) {
        accessGuard.requireAdmin();

        String eventTitle = null;
        if (request.eventId() != null) {
            Event event = eventRepository.findByIdAndDeletedAtIsNull(request.eventId())
                    .orElseThrow(() -> new ResourceNotFoundException("Event " + request.eventId() + " not found"));
            eventTitle = event.getTitle();
        }
        String title = request.title().trim();
        if (title.isEmpty()) {
            throw new BadRequestException("Post title must not be blank");
        }
        Instant now = Instant.now();
        requireValidWindow(request.publishAt(), request.expiresAt());
        if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
            throw new BadRequestException("expiresAt must be in the future");
        }
        String imageUrl = request.imageUrl() == null || request.imageUrl().isBlank() ? null : requireOwnImage(request.imageUrl());

        Post saved = postRepository.save(Post.builder()
                .eventId(request.eventId())
                .kind(request.kind())
                .title(title)
                .body(request.body() == null ? "" : request.body().trim())
                .imageUrl(imageUrl)
                .publishAt(request.publishAt())
                .expiresAt(request.expiresAt())
                .hidden(Boolean.TRUE.equals(request.hidden()))
                .build());
        BusinessAuditLogger.record("post.created", "Post", saved.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                "kind=" + saved.getKind() + (saved.getEventId() == null ? " siteWide" : " event=" + saved.getEventId())
                        + (saved.getPublishAt() == null ? "" : " publishAt=" + saved.getPublishAt())
                        + (saved.getExpiresAt() == null ? "" : " expiresAt=" + saved.getExpiresAt())
                        + (saved.isHidden() ? " hidden" : "")
                        + (saved.getImageUrl() == null ? "" : " image"));
        return PostResponse.from(saved, eventTitle, now);
    }

    @Override
    public Page<PostResponse> list(UUID eventId, boolean siteWide, boolean includeAll, Pageable pageable) {
        if (eventId != null && siteWide) {
            throw new BadRequestException("eventId and siteWide cannot be combined");
        }
        if (includeAll) {
            accessGuard.requireAdmin();
        }
        // A fresh, unsorted request: the ordering is in the queries, and a client-supplied sort must not reach them.
        int size = pageable.isPaged() ? Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        Pageable page = PageRequest.of(pageable.isPaged() ? pageable.getPageNumber() : 0, size);
        Instant now = Instant.now();

        Page<Post> posts;
        if (includeAll) {
            if (eventId != null) {
                posts = postRepository.findAllAdminForEvent(eventId, page);
            } else if (siteWide) {
                posts = postRepository.findSiteWideAdmin(page);
            } else {
                posts = postRepository.findAllAdmin(page);
            }
        } else if (eventId != null) {
            posts = postRepository.findVisibleForEvent(eventId, VISIBLE_EVENT_STATUSES, now, page);
        } else if (siteWide) {
            posts = postRepository.findSiteWide(now, page);
        } else {
            posts = postRepository.findVisible(VISIBLE_EVENT_STATUSES, now, page);
        }

        Set<UUID> eventIds = new HashSet<>();
        posts.forEach(post -> {
            if (post.getEventId() != null) {
                eventIds.add(post.getEventId());
            }
        });
        Map<UUID, String> titles = new HashMap<>();
        if (!eventIds.isEmpty()) {
            eventRepository.findAllById(eventIds).forEach(event -> titles.put(event.getId(), event.getTitle()));
        }
        return posts.map(post -> PostResponse.from(post, post.getEventId() == null ? null : titles.get(post.getEventId()), now));
    }

    @Override
    public PostResponse update(UUID postId, PostUpdateRequest request) {
        accessGuard.requireAdmin();
        Post post = getOrThrow(postId);
        if (Boolean.TRUE.equals(request.clearPublishAt()) && request.publishAt() != null) {
            throw new BadRequestException("publishAt and clearPublishAt cannot be combined");
        }
        if (Boolean.TRUE.equals(request.clearExpiresAt()) && request.expiresAt() != null) {
            throw new BadRequestException("expiresAt and clearExpiresAt cannot be combined");
        }

        List<String> changed = new ArrayList<>();
        if (request.kind() != null) {
            post.setKind(request.kind());
            changed.add("kind");
        }
        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw new BadRequestException("Post title must not be blank");
            }
            post.setTitle(title);
            changed.add("title");
        }
        if (request.body() != null) {
            post.setBody(request.body().trim());
            changed.add("body");
        }
        String previousImage = post.getImageUrl();
        if (request.imageUrl() != null) {
            post.setImageUrl(request.imageUrl().isBlank() ? null : requireOwnImage(request.imageUrl()));
            if (!Objects.equals(previousImage, post.getImageUrl())) {
                changed.add("imageUrl");
            }
        }
        if (request.publishAt() != null || Boolean.TRUE.equals(request.clearPublishAt())) {
            post.setPublishAt(Boolean.TRUE.equals(request.clearPublishAt()) ? null : request.publishAt());
            changed.add("publishAt");
        }
        if (request.expiresAt() != null || Boolean.TRUE.equals(request.clearExpiresAt())) {
            post.setExpiresAt(Boolean.TRUE.equals(request.clearExpiresAt()) ? null : request.expiresAt());
            changed.add("expiresAt");
        }
        // The window is judged on the result, so moving only one end is checked against the other. An expiry that
        // is already in the past is allowed here: it is how an admin ends a post right now.
        requireValidWindow(post.getPublishAt(), post.getExpiresAt());
        if (request.hidden() != null) {
            post.setHidden(request.hidden());
            changed.add("hidden");
        }

        Post saved = postRepository.save(post);
        BusinessAuditLogger.record("post.updated", "Post", postId, BusinessAuditLogger.Outcome.SUCCESS,
                "fields=" + String.join(",", changed));
        // The replaced or removed picture goes away unless something else still uses it (never the one just kept).
        if (previousImage != null && !previousImage.equals(saved.getImageUrl())) {
            uploadedFileUrls.deleteIfUnreferenced(previousImage);
        }
        String eventTitle = saved.getEventId() == null ? null
                : eventRepository.findById(saved.getEventId()).map(Event::getTitle).orElse(null);
        return PostResponse.from(saved, eventTitle, Instant.now());
    }

    @Override
    public void delete(UUID postId) {
        accessGuard.requireAdmin();
        Post post = getOrThrow(postId);
        post.markDeleted();
        postRepository.save(post);
        BusinessAuditLogger.record("post.deleted", "Post", postId, BusinessAuditLogger.Outcome.SUCCESS);
        // Soft-deleted posts are not restorable through the API, so their picture is released like any other unused file.
        if (post.getImageUrl() != null) {
            uploadedFileUrls.deleteIfUnreferenced(post.getImageUrl());
        }
    }

    private Post getOrThrow(UUID postId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post " + postId + " not found"));
    }

    /** The trimmed URL when it is one of our own existing uploads, else a 400. */
    private String requireOwnImage(String url) {
        if (uploadedFileUrls.ownFileName(url).isEmpty()) {
            throw new BadRequestException("imageUrl must be the URL of an image uploaded through POST /api/v1/uploads");
        }
        return url.trim();
    }

    private static void requireValidWindow(Instant publishAt, Instant expiresAt) {
        if (publishAt != null && expiresAt != null && !expiresAt.isAfter(publishAt)) {
            throw new BadRequestException("expiresAt must be after publishAt");
        }
    }
}
