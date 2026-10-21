package com.junaldadlawan.event_ticketing_api.review.repository;

import com.junaldadlawan.event_ticketing_api.review.dto.EventReviewListItem;
import com.junaldadlawan.event_ticketing_api.review.entity.EventReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventReviewRepository extends JpaRepository<EventReview, UUID> {

    Optional<EventReview> findByEventIdAndUserIdAndDeletedAtIsNull(UUID eventId, UUID userId);

    /** {@code [rating, count]} per star given, for the public summary. */
    @Query("select r.rating, count(r) from EventReview r where r.eventId = :eventId and r.deletedAt is null group by r.rating")
    List<Object[]> countByRating(@Param("eventId") UUID eventId);

    /** The organizers' list, with each reviewer's name. */
    @Query(value = "select new com.junaldadlawan.event_ticketing_api.review.dto.EventReviewListItem("
            + "r.id, r.rating, r.comment, trim(concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, ''))), r.createdAt)"
            + " from EventReview r left join User u on u.id = r.userId where r.eventId = :eventId and r.deletedAt is null",
            countQuery = "select count(r) from EventReview r where r.eventId = :eventId and r.deletedAt is null")
    Page<EventReviewListItem> findListItems(@Param("eventId") UUID eventId, Pageable pageable);
}
