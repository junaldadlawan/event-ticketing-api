package com.junaldadlawan.event_ticketing_api.order.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

/**
 * "Transactional/status-bearing" tier per the ERD's audit-column policy -
 * {@code created_by}/{@code created_at}/{@code updated_at} only, no {@code
 * updated_by}/{@code deleted_at} - deliberately does NOT extend {@code
 * Auditable}, same idiom as {@code Cart}/{@code Seat}. An Order is never
 * soft-deleted; later lifecycle changes (refunds, cancellations) are
 * represented via {@code status} transitions, not row deletion.
 * <p>
 * {@code createdBy} is set manually by the service from {@code
 * OrganizationAccessGuard.currentUserId().toString()} rather than through
 * the {@code AuditingEntityListener} machinery - this codebase has no
 * {@code AuditorAware} bean yet, and wiring one up for this one field isn't
 * this dispatch's job.
 * <p>
 * {@code promoCode} stores the code string itself (not a FK to {@code
 * PromoCode}), matching the ERD.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type", nullable = false, length = 20)
    private PayeeType payeeType;

    @Column(name = "payee_id", nullable = false)
    private UUID payeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "promo_code", length = 50)
    private String promoCode;

    /**
     * Nullable: future non-cart order paths (e.g. Phase 7 resale) won't have
     * a cart to reference. A DB-level partial unique index on this column
     * (see V11) is a hard backstop against two Orders ever being created for
     * the same cart, on top of {@code CartRepository.findByIdForUpdate}'s row
     * lock in {@code CheckoutServiceImpl.doCheckout} (code-reviewer CRITICAL
     * 1 - defense in depth, not a replacement for the lock).
     */
    @Column(name = "cart_id")
    private UUID cartId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "total_currency", nullable = false, length = 3))
    })
    private Money total;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
