package com.junaldadlawan.event_ticketing_api.auditlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 13 (BR-NFR-005). Append-only - no setters are ever called on a
 * persisted row, no {@code updatedAt}/{@code updatedBy}/{@code deletedAt} -
 * "a mutable audit trail would defeat its own purpose" (ERD). {@code action}
 * is a free-text {@code "<resource>.<past_tense_verb>"} string (e.g.
 * {@code "refund.issued"}), matching openapi.yaml's own example, not an enum -
 * new sensitive actions can be logged without a schema change.
 */
@Entity
@Table(name = "audit_log_entries")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
