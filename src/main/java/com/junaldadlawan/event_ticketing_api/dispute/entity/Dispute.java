package com.junaldadlawan.event_ticketing_api.dispute.entity;

import com.junaldadlawan.event_ticketing_api.dispute.enums.DisputeStatus;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * "Transactional/status-bearing" tier per the ERD's audit-column policy -
 * {@code created_at}/{@code updated_at} only, no {@code created_by}/{@code
 * deleted_at} (same tier as {@code Order}/{@code Refund}) - EXCEPT the ERD
 * explicitly calls out that {@code Dispute} also gets {@code updated_by},
 * "only where an admin actively edits it after creation" (an admin resolving
 * or dismissing it via {@code DisputeServiceImpl.update} - never set on
 * creation, since the raiser isn't an admin acting on someone else's
 * record).
 */
@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "raised_by", nullable = false)
    private UUID raisedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DisputeStatus status;

    /**
     * Deliberate deviation from openapi.yaml's {@code Dispute} schema: the
     * ERD's {@code disputes} table has no {@code reason} column at all (only
     * {@code resolution}), but {@code DisputeCreate} requires {@code reason}
     * - the raiser's initial complaint has to be persisted somewhere, or it
     * would be silently discarded on every {@code POST /disputes} (same
     * category of documented gap as {@code TicketTypeCreateRequest
     * .quantityTotal} being required when openapi doesn't require it).
     */
    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "resolution", length = 1000)
    private String resolution;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
