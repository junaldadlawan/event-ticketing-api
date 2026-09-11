package com.junaldadlawan.event_ticketing_api.checkin.entity;

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
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional tier per the ERD (BR-CHECKIN-009/010, pure_offline mode
 * only). {@code capturedAt} is client-supplied - when the offline fallback
 * device actually captured the scan, per openapi's {@code
 * submitFallbackScans} request body, which can be well before the
 * reconnect-and-upload moment. {@code syncedAt}/{@code reconciledTicketId}
 * are server-set once reconciliation happens - always immediately in this
 * implementation (the upload call itself proves connectivity, so there's no
 * genuinely deferred "pending reconciliation" state to model here).
 */
@Entity
@Table(name = "fallback_scan_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FallbackScanRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "raw_credential", nullable = false, length = 500)
    private String rawCredential;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "synced_at")
    private Instant syncedAt;

    /** Null if the raw credential never resolved to a real ticket. */
    @Column(name = "reconciled_ticket_id")
    private UUID reconciledTicketId;
}
