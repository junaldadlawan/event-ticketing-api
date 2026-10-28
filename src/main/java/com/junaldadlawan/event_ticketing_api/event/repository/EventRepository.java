package com.junaldadlawan.event_ticketing_api.event.repository;

import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    /** BR-EVENT-007: live drafts that start at or before {@code cutoff} (the oldest first), for the auto-delete sweep. */
    @Query("select e.id from Event e where e.status = com.junaldadlawan.event_ticketing_api.event.enums.EventStatus.DRAFT"
            + " and e.deletedAt is null and e.startAt <= :cutoff order by e.startAt")
    List<UUID> findDraftIdsStartingBy(@Param("cutoff") Instant cutoff, org.springframework.data.domain.Pageable page);

    /** BR-EVENT-007: drafts that start after {@code from} and at or before {@code until}: due for the "will be deleted" warning. */
    @Query("select e from Event e where e.status = com.junaldadlawan.event_ticketing_api.event.enums.EventStatus.DRAFT"
            + " and e.deletedAt is null and e.startAt > :from and e.startAt <= :until")
    List<Event> findDraftsStartingBetween(@Param("from") Instant from, @Param("until") Instant until);

    /**
     * Soft deletes a draft that is still due (a conditional UPDATE, not an entity save, so it cannot overwrite a concurrent change, and exactly one
     * caller gets 1 back). Same effect as the manual delete: the row stays, {@code deletedAt} is set.
     */
    @Modifying
    @Transactional
    @Query("update Event e set e.deletedAt = :now, e.updatedAt = :now where e.id = :id"
            + " and e.status = com.junaldadlawan.event_ticketing_api.event.enums.EventStatus.DRAFT and e.deletedAt is null and e.startAt <= :cutoff")
    int softDeleteDraftIfDue(@Param("id") UUID id, @Param("cutoff") Instant cutoff, @Param("now") Instant now);

    Optional<Event> findByShareCodeAndDeletedAtIsNull(String shareCode);

    /** Sets the event's share code only while it has none (1 = set, 0 = it already had one); a taken code violates the unique index. */
    @Modifying
    @Transactional
    @Query(value = "update events set share_code = :code where id = :id and share_code is null", nativeQuery = true)
    int assignShareCode(@Param("id") UUID id, @Param("code") String code);

    /** The stored share code, read from the database (an entity loaded earlier in the same request would still show the old value). */
    @Query("select e.shareCode from Event e where e.id = :id")
    Optional<String> findShareCode(@Param("id") UUID id);

    /** The picture URLs of one event, read without loading the event (for code that runs outside a web request). */
    @Query("select i from Event e join e.images i where e.id = :id")
    List<String> findImageUrls(@Param("id") UUID id);

    boolean existsByTicketPrefix(String ticketPrefix);

    /** True when an event that has not ended (and is not cancelled) uses the seat layout. */
    boolean existsByLayoutIdAndDeletedAtIsNullAndEndAtAfterAndStatusNot(UUID layoutId, Instant now, EventStatus status);

    Optional<Event> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * BR-NOTIFY-002: live or completed events that may have a lifecycle notification due - starting within
     * {@code startsBefore} (now + 24h) and not ended before {@code endedAfter} (how long review requests stay due).
     */
    @Query("select e from Event e where e.deletedAt is null and e.status in :statuses and e.startAt <= :startsBefore and e.endAt > :endedAfter")
    List<Event> findLifecycleCandidates(@Param("statuses") Collection<EventStatus> statuses, @Param("startsBefore") Instant startsBefore,
                                        @Param("endedAfter") Instant endedAfter);

    /** Phase 14 (BR-ANALYTICS-002): "event volume" - total events ever created, platform-wide, not just currently-published ones. */
    long countByDeletedAtIsNull();

    /** BR-EVENT-005: live events whose end time has passed, for the completion sweep. */
    @Query("select e.id from Event e where e.status in :statuses and e.endAt <= :now and e.deletedAt is null")
    List<UUID> findEndedIds(@Param("statuses") Collection<EventStatus> statuses, @Param("now") Instant now);

    /**
     * Moves one ended event to COMPLETED only if it is still live, as a single conditional UPDATE, so the job and a
     * read racing on the same event complete it once (the one that gets 1 back records it).
     */
    @Modifying
    @Transactional
    @Query("update Event e set e.status = :completed, e.updatedAt = :now"
            + " where e.id = :id and e.status in :statuses and e.endAt <= :now and e.deletedAt is null")
    int markCompletedIfEnded(@Param("id") UUID id, @Param("statuses") Collection<EventStatus> statuses,
                             @Param("completed") EventStatus completed, @Param("now") Instant now);
}
