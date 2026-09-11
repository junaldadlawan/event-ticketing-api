package com.junaldadlawan.event_ticketing_api.checkin.entity;

import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInMode;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Managed-resource-ish tier per the ERD: {@code created_at}/{@code
 * updated_at}/{@code updated_by}, deliberately no {@code created_by} (the
 * ERD doesn't list one) and no {@code deleted_at} (reconfigured in place,
 * never soft-deleted). Own generated id + a unique {@code event_id} column
 * rather than the ERD's literal "event_id as primary key" depiction - same
 * deviation already established for {@code ResalePolicy}/{@code RefundPolicy}.
 * <p>
 * BR-CHECKIN-004: {@code mode} is always an explicit, manual organizer
 * choice - nothing in this codebase infers or auto-switches it based on
 * observed network conditions.
 */
@Entity
@Table(name = "check_in_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private CheckInMode mode;

    /** BR-CHECKIN-007: default 300 (5 minutes), organizer-overridable per event. */
    @Builder.Default
    @Column(name = "offline_fallback_expiry_seconds", nullable = false)
    private int offlineFallbackExpirySeconds = 300;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
