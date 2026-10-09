package com.junaldadlawan.event_ticketing_api.post.service;

import com.junaldadlawan.event_ticketing_api.post.dto.PostCreateRequest;
import com.junaldadlawan.event_ticketing_api.post.dto.PostResponse;
import com.junaldadlawan.event_ticketing_api.post.dto.PostUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PostService {

    /**
     * Admin only. A post for an event needs that event to exist and not be deleted (404). {@code expiresAt} must be
     * in the future and after {@code publishAt} (400).
     */
    PostResponse create(PostCreateRequest request);

    /**
     * Newest first. {@code eventId} = only that event's posts; {@code siteWide} = only site-wide ones; neither =
     * site-wide plus those of visible events. For everyone ({@code includeAll} false) only live posts are listed: not
     * hidden, already published, not expired, and never those of draft, cancelled, suspended or deleted events.
     * {@code includeAll} (admin only, 403 otherwise) lists every post that is not deleted, whatever its state.
     * The page size is capped at 50 and the sort the client asked for is ignored.
     */
    Page<PostResponse> list(UUID eventId, boolean siteWide, boolean includeAll, Pageable pageable);

    /** Admin only. Changes the given fields; see {@link PostUpdateRequest}. */
    PostResponse update(UUID postId, PostUpdateRequest request);

    /** Admin only. Soft delete. */
    void delete(UUID postId);
}
