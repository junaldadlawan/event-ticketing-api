package com.junaldadlawan.event_ticketing_api.notification.entity;

import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationChannel;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationStatus;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
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
 * System-triggered append-only tier per the ERD: only {@code createdAt} (the
 * trigger timestamp, distinct from {@code sentAt}), no updatedAt/updatedBy/
 * deletedAt - same tier as {@code CheckInRecord}/{@code TicketTransfer}.
 * {@code status} does transition in place after creation (PENDING -> SENT/
 * FAILED), but that's a set-once-later field, not a general revision-
 * tracking concern.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private NotificationChannel channel;

    /** E.g. "Order"/"Refund"/"WaitlistEntry" - nullable since not every notification is tied to one row (e.g. a future EVENT_REMINDER). */
    @Column(name = "related_object_type", length = 50)
    private String relatedObjectType;

    @Column(name = "related_object_id")
    private UUID relatedObjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private NotificationStatus status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
