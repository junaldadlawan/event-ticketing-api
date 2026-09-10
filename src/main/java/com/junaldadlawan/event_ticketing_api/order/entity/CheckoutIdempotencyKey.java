package com.junaldadlawan.event_ticketing_api.order.entity;

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
 * Purely internal bookkeeping for {@code POST /carts/{cartId}/checkout}'s
 * required {@code Idempotency-Key} header (BR-NFR-008) - never exposed via
 * any API response.
 * <p>
 * {@code id} IS the client-supplied {@code Idempotency-Key} header value
 * (not server-generated), so it's declared {@code @Id} without {@code
 * @GeneratedValue}.
 * <p>
 * {@code orderId} is null while a request is genuinely in-flight (the row
 * has been claimed but checkout hasn't finished yet) and is set once
 * checkout completes successfully. See {@code CheckoutServiceImpl}/{@code
 * CheckoutIdempotencyKeyManager} for how this distinction drives the
 * replay-vs-in-flight-vs-fresh branching.
 * <p>
 * Implements {@link Persistable} for exactly the same reason as {@code
 * OrganizationMember} (mirrors its {@code isNew}/{@code @PostLoad}/{@code
 * @PostPersist} mechanism): {@code id} is assigned by the caller, not
 * generated, so without this Spring Data's default "non-null id -> existing"
 * heuristic would route every {@code save()} through {@code merge()} instead
 * of {@code persist()} - including a brand-new {@code claim()} attempt. That
 * silently defeated the whole point of {@link
 * CheckoutIdempotencyKeyManager#claim}'s race protection: two concurrent
 * transactions claiming the same key could both have {@code merge()} see the
 * other's just-committed row and treat it as an update rather than raising a
 * real constraint violation (code-reviewer CRITICAL 2). A freshly built
 * instance now correctly persists (and throws on a genuine duplicate-key
 * race); a loaded-then-later-saved instance (the {@code orderId} backfill in
 * {@code claim()}) correctly merges.
 */
@Entity
@Table(name = "checkout_idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutIdempotencyKey implements Persistable<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

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
