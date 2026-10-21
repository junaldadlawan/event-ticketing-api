package com.junaldadlawan.event_ticketing_api.recap.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
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

import java.util.UUID;

/** BR-EVENT-RECAP-001: one message (text, a picture, or both) in a completed event's recap feed. */
@Entity
@Table(name = "event_recap_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRecapMessage extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    /** Never null; empty when the message is only a picture. */
    @Builder.Default
    @Column(name = "body", nullable = false, length = 2000)
    private String body = "";

    /** One of this server's own uploads (POST /uploads), or null. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;
}
