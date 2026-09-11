package com.junaldadlawan.event_ticketing_api.tickettransfer.entity;

import com.junaldadlawan.event_ticketing_api.tickettransfer.enums.TransferSource;
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
 * Immutable, append-only historical record (Phase 7) per the ERD's
 * audit-column policy — a single creation timestamp ({@code transferredAt})
 * and nothing else, same tier as {@code CheckInRecord}/{@code
 * FallbackScanRecord}. One row per ownership change on a {@code Ticket} -
 * the ticket row itself persists across transfers (same id), so a ticket can
 * accumulate many of these over its lifetime ("Ticket ||--o{ TicketTransfer:
 * ownership history").
 */
@Entity
@Table(name = "ticket_transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "from_user_id", nullable = false)
    private UUID fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private UUID toUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private TransferSource source;

    @CreationTimestamp
    @Column(name = "transferred_at", updatable = false, nullable = false)
    private Instant transferredAt;
}
