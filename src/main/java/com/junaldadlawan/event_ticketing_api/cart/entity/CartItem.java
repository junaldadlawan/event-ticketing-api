package com.junaldadlawan.event_ticketing_api.cart.entity;

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
 * "Immutable, append-only" tier per the ERD's audit-column policy — only a
 * creation timestamp, no {@code updated_at}/{@code deleted_at}. A CartItem
 * is never edited in place: quantity/seat changes happen by removing and
 * re-adding (see {@code CartService.removeItem}/{@code addItem}), and hold
 * expiry/release deletes the row rather than mutating a status field.
 * <p>
 * {@code quantity} is meaningful for GENERAL_ADMISSION; for
 * RESERVED_SEATING it is always {@code 1}, since one CartItem = one specific
 * seat.
 */
@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "seat_id")
    private UUID seatId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "hold_expires_at", nullable = false)
    private Instant holdExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
