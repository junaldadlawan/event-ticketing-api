package com.junaldadlawan.event_ticketing_api.order.entity;

import com.junaldadlawan.event_ticketing_api.order.enums.PendingCheckoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
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

/** A checkout waiting for the payment provider's webhook, see {@link PendingCheckoutStatus}. Status-bearing tier: timestamps only. */
@Entity
@Table(name = "pending_checkouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingCheckout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    /** The buyer's checkout Idempotency-Key: a repeated start with it returns this same row. */
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    /** The provider's payment intent ({@code pi_...}). The webhook finds this row by it. */
    @Column(name = "provider_intent_id", nullable = false, length = 100)
    private String providerIntentId;

    /** Lets the browser attach a card to this one intent. Only ever shown to the buyer who started the checkout. */
    @Column(name = "client_key", nullable = false)
    private String clientKey;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PendingCheckoutStatus status;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
