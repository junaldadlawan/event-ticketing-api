package com.junaldadlawan.event_ticketing_api.gatewayfee.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * The one platform-wide payment gateway fee setting (row id 1, created by the migration): a percentage and an optional flat amount
 * (the domestic rate), optionally a second rate for cards issued abroad, and who pays it. Changing it never touches orders already placed (they carry their own snapshot).
 */
@Entity
@Table(name = "payment_gateway_fee_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayFeeSetting extends Auditable {

    public static final short ROW_ID = 1;

    @Id
    @Column(name = "id")
    @Builder.Default
    private Short id = ROW_ID;

    @Column(name = "percentage", nullable = false, precision = 6, scale = 3)
    @Builder.Default
    private BigDecimal percentage = BigDecimal.ZERO;

    /** The fixed part of the fee, in minor units (1339 = PHP 13.39), applied per order only when the order is in {@link #flatCurrency}. */
    @Column(name = "flat_amount", nullable = false)
    private long flatAmount;

    @Column(name = "flat_currency", nullable = false, length = 3)
    @Builder.Default
    private String flatCurrency = "PHP";

    /** The rate for a card issued abroad (the whole rate, not an addition to the domestic one). Null means the same as the domestic rate. */
    @Column(name = "foreign_percentage", precision = 6, scale = 3)
    private BigDecimal foreignPercentage;

    /** The fixed part of the foreign rate in minor units, in {@link #flatCurrency}. Null with a null {@link #foreignPercentage}: same as domestic. */
    @Column(name = "foreign_flat_amount")
    private Long foreignFlatAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer", nullable = false, length = 20)
    @Builder.Default
    private GatewayFeePayer payer = GatewayFeePayer.CUSTOMER;
}
