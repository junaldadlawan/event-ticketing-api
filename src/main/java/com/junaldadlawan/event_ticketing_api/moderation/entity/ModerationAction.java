package com.junaldadlawan.event_ticketing_api.moderation.entity;

import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationActionType;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationTargetType;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail - no {@code Auditable}, no {@code updatedAt}/
 * {@code updatedBy}, and no setters are ever called on a persisted row
 * (mirrors the ERD's stated philosophy for {@code AuditLogEntry}: "a
 * mutable audit trail would defeat its own purpose" - same reasoning
 * applies here even though this predates Phase 13's actual {@code
 * AuditLogEntry}). Every admin action against an {@code Organization}/
 * {@code Event}/{@code User} is recorded as its own immutable row, which
 * doubles as the reason/audit trail BR-ADMIN-002 calls for.
 */
@Entity
@Table(name = "moderation_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ModerationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ModerationActionType action;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    /**
     * The target's status/state BEFORE this action was applied, captured so
     * a later {@code REINSTATE} knows exactly what to restore (a suspended
     * {@code Event} could have been {@code DRAFT}, {@code PUBLISHED},
     * {@code ON_SALE}, or {@code SOLD_OUT} beforehand) - stored as a plain
     * string since {@code Organization}/{@code Event}/{@code User} each
     * have their own distinct status enum.
     */
    @Column(name = "previous_status", length = 20)
    private String previousStatus;

    @Column(name = "performed_by", nullable = false)
    private UUID performedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
