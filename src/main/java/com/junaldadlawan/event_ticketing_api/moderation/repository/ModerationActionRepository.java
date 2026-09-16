package com.junaldadlawan.event_ticketing_api.moderation.repository;

import com.junaldadlawan.event_ticketing_api.moderation.entity.ModerationAction;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationActionType;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, UUID> {

    /**
     * {@code REINSTATE}'s lookback: the most recent {@code SUSPEND} row for
     * this target carries the status to restore in its {@code
     * previousStatus} column.
     */
    Optional<ModerationAction> findFirstByTargetIdAndActionOrderByCreatedAtDesc(UUID targetId, ModerationActionType action);

    /** {@code GET /admin/moderation-actions} - both filters optional, see {@code ModerationActionServiceImpl.list}. */
    Page<ModerationAction> findByTargetTypeAndTargetId(ModerationTargetType targetType, UUID targetId, Pageable pageable);

    Page<ModerationAction> findByTargetType(ModerationTargetType targetType, Pageable pageable);

    Page<ModerationAction> findByTargetId(UUID targetId, Pageable pageable);
}
