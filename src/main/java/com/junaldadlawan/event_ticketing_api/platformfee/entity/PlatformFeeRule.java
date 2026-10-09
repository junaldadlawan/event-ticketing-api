package com.junaldadlawan.event_ticketing_api.platformfee.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeScope;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeType;
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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An admin-set platform fee ("admin cut"). At most one live rule per scope: the platform default
 * ({@code scopeId} null), one organization, or one event. A PERCENTAGE rule uses {@code percentage}; a FLAT rule uses
 * {@code flatAmount} + {@code flatCurrency}. Soft-deleted through {@link Auditable#markDeleted()}; deleting an
 * override makes the next less specific rule apply again.
 */
@Entity
@Table(name = "platform_fee_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformFeeRule extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private FeeScope scope;

    /** Null for the PLATFORM scope; the organization or event id otherwise. */
    @Column(name = "scope_id")
    private UUID scopeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = 20)
    private FeeType type;

    /** 0-100 with up to two decimals; set for PERCENTAGE, null for FLAT. */
    @Column(name = "percentage", precision = 5, scale = 2)
    private BigDecimal percentage;

    /** Minor units per order; set for FLAT, null for PERCENTAGE. */
    @Column(name = "flat_amount")
    private Long flatAmount;

    @Column(name = "flat_currency", length = 3)
    private String flatCurrency;
}
