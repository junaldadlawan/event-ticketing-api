package com.junaldadlawan.event_ticketing_api.recap.repository;

import com.junaldadlawan.event_ticketing_api.recap.entity.EventRecapMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRecapMessageRepository extends JpaRepository<EventRecapMessage, UUID> {

    Optional<EventRecapMessage> findByIdAndEventIdAndDeletedAtIsNull(UUID id, UUID eventId);

    /** The feed, oldest first, each with its author's name: {@code [message, firstName, lastName]}. */
    @Query("select m, u.firstName, u.lastName from EventRecapMessage m left join User u on u.id = m.authorUserId"
            + " where m.eventId = :eventId and m.deletedAt is null order by m.createdAt asc, m.id asc")
    List<Object[]> findFeed(@Param("eventId") UUID eventId);
}
