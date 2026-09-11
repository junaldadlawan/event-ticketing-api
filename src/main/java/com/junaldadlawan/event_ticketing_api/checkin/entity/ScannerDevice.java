package com.junaldadlawan.event_ticketing_api.checkin.entity;

import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional tier per the ERD: {@code created_at}/{@code created_by}/
 * {@code updated_at}/{@code updated_by}, no {@code deleted_at}
 * (deactivation is the {@code REVOKED} status, not a soft delete).
 * <p>
 * Deliberately NO credential column - a device's credential is an
 * HMAC-signed token re-derived from its own id (see {@code
 * ScannerDeviceCredentialService}), the same "deliberately re-derivable,
 * not separately stored" design as {@code TicketCredentialService} (Phase
 * 6a). Revocation (BR-CHECKIN: "revoke a device's credential immediately")
 * works by checking {@code status == ACTIVE} live at request time in
 * {@code DeviceAuthenticationFilter}, not by invalidating stored state.
 * <p>
 * {@code id} is assigned by {@code ScannerDeviceServiceImpl} (not {@code
 * @GeneratedValue}) - same reasoning as {@code Ticket}: the id must be
 * known BEFORE the credential is computed, since the credential embeds it
 * ({@code ScannerDeviceCredentialService}). Implements {@link Persistable}
 * for the same reason as {@code Ticket}/{@code CheckoutIdempotencyKey}.
 */
@Entity
@Table(name = "scanner_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScannerDevice implements Persistable<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "device_label", nullable = false, length = 255)
    private String deviceLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScannerDeviceStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
