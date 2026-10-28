package com.junaldadlawan.event_ticketing_api.event.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import com.junaldadlawan.event_ticketing_api.event.cache.PublicEventCacheEvictionListener;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@EntityListeners(PublicEventCacheEvictionListener.class)
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description",nullable = false, length = 2000)
    private String description;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "venue_id")
    private UUID venueId;

    /** The organization seat layout its reserved seats were copied from (set by the first ticket type that copies seats). */
    @Column(name = "layout_id")
    private UUID layoutId;

    @Column(name = "start_time", nullable = false)
    private Instant startAt;

    @Column(name = "end_time", nullable = false)
    private Instant endAt;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @ElementCollection
    @CollectionTable(
            name = "event_images",
            joinColumns = @JoinColumn(name = "event_id"),
            // No FK constraints, matching every other table in this schema (see V6 migration).
            foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderColumn(name = "sort_order")
    @Column(name = "url", length = 2048)
    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status = EventStatus.DRAFT;

    /** BR-TICKET-GATE-001: gates open this many minutes before the start (0-1440); null = not set. Display only. */
    @Column(name = "gate_opens_minutes_before")
    private Integer gateOpensMinutesBefore;

    /** BR-NOTIFY-002: scheduled notifications due before this moment are not sent (set on publish and when the event moves). */
    @Column(name = "notifications_from")
    private Instant notificationsFrom;

    @Column(name = "ticket_prefix", nullable = false, length = 3)
    private String ticketPrefix;

    // Share tracking. Read-only for JPA on purpose: saving an event must never write back an old counter, so only the
    // repository's atomic updates (ShareCounterRepository, EventRepository.assignShareCode) change these columns.
    @Column(name = "share_code", insertable = false, updatable = false)
    private String shareCode;

    @Column(name = "share_click_count", insertable = false, updatable = false)
    private long shareClickCount;

    @Column(name = "share_copy_count", insertable = false, updatable = false)
    private long shareCopyCount;

    @Column(name = "share_qr_count", insertable = false, updatable = false)
    private long shareQrCount;

    @Column(name = "share_link_open_count", insertable = false, updatable = false)
    private long shareLinkOpenCount;

    @Column(name = "share_qr_scan_count", insertable = false, updatable = false)
    private long shareQrScanCount;

    @Column(name = "share_link_buy_count", insertable = false, updatable = false)
    private long shareLinkBuyCount;

    @Column(name = "share_qr_buy_count", insertable = false, updatable = false)
    private long shareQrBuyCount;
}
