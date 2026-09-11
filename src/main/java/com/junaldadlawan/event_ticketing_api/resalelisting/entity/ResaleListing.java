package com.junaldadlawan.event_ticketing_api.resalelisting.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.resalelisting.enums.ResaleListingStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 * Transactional/status-bearing tier per the ERD's audit-column policy -
 * {@code listed_at}/{@code updated_at} only, no {@code created_by}/{@code
 * updated_by}/{@code deleted_at}, same tier as {@code Order}/{@code Ticket}.
 * A ticket has at most one ACTIVE listing at a time, backstopped by V14's
 * partial unique index on {@code (ticket_id) WHERE status = 'ACTIVE'}.
 */
@Entity
@Table(name = "resale_listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResaleListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    /** Denormalized for query convenience, same reasoning as {@code Ticket.eventId}. */
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "asking_price_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "asking_price_currency", nullable = false, length = 3))
    })
    private Money askingPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResaleListingStatus status;

    @CreationTimestamp
    @Column(name = "listed_at", updatable = false, nullable = false)
    private Instant listedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "buyer_order_id")
    private UUID buyerOrderId;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
