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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * "Transactional/status-bearing" tier per the ERD's audit-column policy —
 * {@code created_at}/{@code updated_at} only, no soft delete, no
 * {@code created_by}/{@code updated_by} — deliberately does NOT extend
 * {@code Auditable}, same idiom as {@code Seat}/{@code OrganizationMember}.
 * <p>
 * No {@code status} field: the roadmap mentions an active/converted/
 * abandoned enum, but it's absent from both the ERD and openapi.yaml, and no
 * cart-abandonment endpoint exists anywhere — treated as a stray idea that
 * didn't make it into the settled design.
 * <p>
 * No persisted {@code total} column: computed live in the service/response
 * layer from the cart's current items + applied promo code, avoiding a
 * persisted-total-drifts-from-reality problem.
 * <p>
 * {@code promoCodeId} is a reference to an applied {@link
 * com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode}, not a
 * frozen snapshot — since no PromoCode update endpoint exists, computing the
 * discount live from the referenced PromoCode at read time is safe and
 * avoids a stale-snapshot consistency problem.
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "promo_code_id")
    private UUID promoCodeId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
