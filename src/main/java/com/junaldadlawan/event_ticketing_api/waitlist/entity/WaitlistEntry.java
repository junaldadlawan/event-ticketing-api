package com.junaldadlawan.event_ticketing_api.waitlist.entity;

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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, append-only tier per the ERD's audit-column policy - a single
 * creation timestamp, same tier as {@code CartItem}/{@code TicketTransfer}.
 * {@code notifiedAt}/{@code offerExpiresAt} are set-once-later fields, not a
 * general revision-tracking {@code updatedAt}.
 * <p>
 * Phase 9 confirmed decision: this dispatch implements join/position
 * tracking (BR-WAIT-001) only - the active trigger that notifies
 * waitlisted users in FIFO order when inventory frees up (BR-WAIT-002/003)
 * is explicitly out of scope per the roadmap's own framing ("depends on
 * Phase 11's notification trigger existing to be meaningful"), since Phase
 * 11 (Notifications) doesn't exist yet and there's no delivery mechanism to
 * trigger. {@code notifiedAt}/{@code offerExpiresAt} therefore stay {@code
 * null} for every row this phase ever creates - nothing populates them yet.
 */
@Entity
@Table(name = "waitlist_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** Null means "waiting for the event generally" (openapi: "Omit to wait for the event generally"). */
    @Column(name = "ticket_type_id")
    private UUID ticketTypeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 1-based, FIFO by join order within this (eventId, ticketTypeId) scope (BR-WAIT-002). */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "offer_expires_at")
    private Instant offerExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
