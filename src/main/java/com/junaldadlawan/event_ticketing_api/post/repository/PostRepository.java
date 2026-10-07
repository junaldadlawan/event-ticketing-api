package com.junaldadlawan.event_ticketing_api.post.repository;

import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * The public listings, newest first (by the moment a post went live: its publish time, else its creation time).
 * A public post is not deleted, not hidden, already published and not yet expired at {@code now}; a post of an
 * event is only public while that event exists, is not deleted and is in one of {@code visibleStatuses} (drafts,
 * cancelled and suspended events are not public). The admin listings ({@code *Admin}) skip the hidden / date /
 * event-status rules and show every post that is not deleted. The ordering is part of each query, so callers pass
 * an unsorted {@link Pageable}.
 */
public interface PostRepository extends JpaRepository<Post, UUID> {

    String LIVE = "p.deletedAt is null and p.hidden = false "
            + "and (p.publishAt is null or p.publishAt <= :now) and (p.expiresAt is null or p.expiresAt > :now)";
    String EVENT_VISIBLE = "exists (select 1 from Event e where e.id = p.eventId and e.deletedAt is null "
            + "and e.status in :visibleStatuses)";
    String NEWEST_FIRST = " order by coalesce(p.publishAt, p.createdAt) desc, p.id desc";

    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    /** Site-wide posts plus the posts of visible events. */
    @Query("select p from Post p where " + LIVE + " and (p.eventId is null or " + EVENT_VISIBLE + ")" + NEWEST_FIRST)
    Page<Post> findVisible(@Param("visibleStatuses") Collection<EventStatus> visibleStatuses,
                           @Param("now") Instant now, Pageable pageable);

    /** Only the posts of this event, and only if the event itself is visible. */
    @Query("select p from Post p where " + LIVE + " and p.eventId = :eventId and " + EVENT_VISIBLE + NEWEST_FIRST)
    Page<Post> findVisibleForEvent(@Param("eventId") UUID eventId,
                                   @Param("visibleStatuses") Collection<EventStatus> visibleStatuses,
                                   @Param("now") Instant now, Pageable pageable);

    /** Only site-wide posts. */
    @Query("select p from Post p where " + LIVE + " and p.eventId is null" + NEWEST_FIRST)
    Page<Post> findSiteWide(@Param("now") Instant now, Pageable pageable);

    /** Admin: every post that is not deleted, whatever its state. */
    @Query("select p from Post p where p.deletedAt is null" + NEWEST_FIRST)
    Page<Post> findAllAdmin(Pageable pageable);

    /** Admin: every post of this event that is not deleted. */
    @Query("select p from Post p where p.deletedAt is null and p.eventId = :eventId" + NEWEST_FIRST)
    Page<Post> findAllAdminForEvent(@Param("eventId") UUID eventId, Pageable pageable);

    /** Admin: every site-wide post that is not deleted. */
    @Query("select p from Post p where p.deletedAt is null and p.eventId is null" + NEWEST_FIRST)
    Page<Post> findSiteWideAdmin(Pageable pageable);
}
