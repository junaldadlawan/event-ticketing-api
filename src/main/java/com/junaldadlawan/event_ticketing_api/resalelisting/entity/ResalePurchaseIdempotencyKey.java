package com.junaldadlawan.event_ticketing_api.resalelisting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Purely internal bookkeeping for {@code POST
 * /resale-listings/{listingId}/purchase}'s required {@code Idempotency-Key}
 * header (BR-NFR-008) - mirrors {@code CheckoutIdempotencyKey} exactly
 * (same {@code id}-is-the-header-value / {@code orderId}-null-means-in-flight
 * / {@link Persistable} reasoning), scoped to {@code listingId} instead of
 * {@code cartId}. Kept as its own table/entity rather than widening the
 * already-shipped Phase 5b {@code checkout_idempotency_keys} table.
 */
@Entity
@Table(name = "resale_purchase_idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResalePurchaseIdempotencyKey implements Persistable<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "order_id")
    private UUID orderId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

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
