package com.junaldadlawan.event_ticketing_api.order.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeScope;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeType;
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

import java.math.BigDecimal;
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

    /**
     * What the platform fee ("admin cut") came to on this order, in the same currency as {@code total} (0 when no fee rule applied).
     * It is part of {@code total} only when {@link #platformFeePayer} is CUSTOMER (or null, an order from before the payer existed);
     * when it is ORGANIZATION it is deducted from the payout, and when PLATFORM it is waived.
     */
    @Builder.Default
    @Column(name = "platform_fee_amount", nullable = false)
    private long platformFeeAmount = 0L;

    /** Snapshot of the fee rule that produced the fee (null when none applied), so rule changes never rewrite old orders. */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform_fee_scope", length = 20)
    private FeeScope platformFeeScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_fee_type", length = 20)
    private FeeType platformFeeType;

    @Column(name = "platform_fee_percentage", precision = 5, scale = 2)
    private BigDecimal platformFeePercentage;

    @Column(name = "platform_fee_flat_amount")
    private Long platformFeeFlatAmount;

    /** Snapshot of who bears the platform fee (null on orders from before the payer existed: the customer paid it). */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform_fee_payer", length = 20)
    private com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer platformFeePayer;

    /** The part of the platform fee the buyer paid on top of the price (the whole fee when the customer bears it, else 0). */
    public long buyerPlatformFee() {
        return platformFeePayer == null || platformFeePayer == com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer.CUSTOMER ? platformFeeAmount : 0L;
    }

    /**
     * What the payment gateway's processing fee came to on this order (0 when none applied). It is part of {@code total} only when
     * {@link #gatewayFeePayer} is CUSTOMER; when it is ORGANIZATION it is deducted from the payout, and when PLATFORM it is absorbed.
     */
    @Builder.Default
    @Column(name = "gateway_fee_amount", nullable = false)
    private long gatewayFeeAmount = 0L;

    /** Snapshot of who bears the gateway fee (null when none applied), so setting changes never rewrite old orders. */
    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_fee_payer", length = 20)
    private com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer gatewayFeePayer;

    @Column(name = "gateway_fee_percentage", precision = 6, scale = 3)
    private BigDecimal gatewayFeePercentage;

    @Column(name = "gateway_fee_flat_amount")
    private Long gatewayFeeFlatAmount;

    /** What the gateway really kept (its fee plus any foreign-card fee), from its own payment record; null until known (see GatewayFeeActualSyncService). */
    @Column(name = "gateway_fee_actual_amount")
    private Long gatewayFeeActualAmount;

    /** Where the card was issued, as the gateway reported with the payment; null when it did not say (and on older orders). */
    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_fee_card_origin", length = 10)
    private com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeeCardOrigin gatewayFeeCardOrigin;

    /** The fee that really applied given the card: the foreign fee for a foreign card, otherwise {@link #gatewayFeeAmount}. Null on older orders. */
    @Column(name = "gateway_fee_applied_amount")
    private Long gatewayFeeAppliedAmount;

    /** Snapshot of the foreign rate in force when the order was placed (null when the foreign card costs the domestic rate). */
    @Column(name = "gateway_fee_foreign_percentage", precision = 6, scale = 3)
    private BigDecimal gatewayFeeForeignPercentage;

    @Column(name = "gateway_fee_foreign_flat_amount")
    private Long gatewayFeeForeignFlatAmount;

    /**
     * What comes off the organizer's payout for the gateway fee: nothing when the platform bears it; the fee the customer paid when the customer
     * bears it (a foreign card costing more is absorbed by the platform, the organizer keeps the ticket price); the fee that really applied when
     * the organization bears it.
     */
    public long gatewayFeeDeduction() {
        if (gatewayFeePayer == null || gatewayFeePayer == com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer.PLATFORM) {
            return 0L;
        }
        if (gatewayFeePayer == com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer.ORGANIZATION && gatewayFeeAppliedAmount != null) {
            return gatewayFeeAppliedAmount;
        }
        return gatewayFeeAmount;
    }

    /** The part of the gateway fee the buyer paid on top of the price (the whole fee when the customer bears it, else 0). */
    public long buyerGatewayFee() {
        return gatewayFeePayer == com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer.CUSTOMER ? gatewayFeeAmount : 0L;
    }

    /** The payout this order was settled in; null until an admin generates the payout that covers it. */
    @Column(name = "payout_id")
    private UUID payoutId;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
