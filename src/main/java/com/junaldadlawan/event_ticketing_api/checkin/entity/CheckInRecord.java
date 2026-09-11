package com.junaldadlawan.event_ticketing_api.checkin.entity;

import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
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
 * Immutable, append-only tier per the ERD: a single creation timestamp
 * ({@code scannedAt}) and nothing else, same tier as {@code
 * TicketTransfer}/{@code WaitlistEntry}. One row per scan attempt that
 * resolved to a real ticket (BR-CHECKIN-001/002) - {@code sourceType}
 * distinguishes a live {@code /check-in/validate} call ({@code
 * SCANNER_DEVICE}, {@code sourceId} = the device's id) from a later-
 * reconciled {@code /check-in/fallback-scans} entry ({@code
 * RECONCILED_FALLBACK}, {@code sourceId} = the {@code FallbackScanRecord}'s
 * id).
 */
@Entity
@Table(name = "check_in_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private CheckInSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private CheckInResult result;

    @CreationTimestamp
    @Column(name = "scanned_at", updatable = false, nullable = false)
    private Instant scannedAt;
}
