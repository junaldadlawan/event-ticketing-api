package com.junaldadlawan.event_ticketing_api.event.entity;

import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id")
    private String organizationId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description",nullable = false, length = 2000)
    private String description;

    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "venue")
    private int venue;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "image", nullable = true)
    private Byte image;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "ticket_prefix", nullable = false, length = 3)
    private String ticketPrefix;

    @CreatedBy
    @Column(name = "created_by", updatable = false, insertable = false, nullable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = true)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = true)
    private Instant updatedAt;

}
